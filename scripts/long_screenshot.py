import subprocess
import time
import os
import sys
from PIL import Image
from io import BytesIO

# Configuration
ADB_PATH = r"C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SAVE_DIR = os.path.join(os.environ["USERPROFILE"], "Desktop", "ADB_Screenshots")
MAX_PAGES = 15  # Safety limit
SCROLL_DURATION_MS = 1000
SCROLL_WAIT_MS = 1500  # Wait for inertial scroll to stop

def get_screen_size():
    try:
        output = subprocess.check_output([ADB_PATH, "shell", "wm", "size"]).decode("utf-8")
        # Physical size: 1080x2400
        parts = output.strip().split(":")[-1].strip().split("x")
        return int(parts[0]), int(parts[1])
    except Exception as e:
        print(f"Error getting screen size: {e}")
        return 1080, 2400 # Default fallback

def capture_screenshot():
    try:
        # Capture strictly to stdout to avoid file I/O on device
        # Note: 'exec-out' is binary safe on newer adb
        result = subprocess.run([ADB_PATH, "exec-out", "screencap", "-p"], capture_output=True)
        if result.returncode == 0:
            return Image.open(BytesIO(result.stdout))
    except Exception as e:
        print(f"Error capturing screenshot: {e}")
    return None

def scroll_down(width, height):
    # Scroll from bottom 3/4 to top 1/4
    start_x = width // 2
    start_y = int(height * 0.75)
    end_x = width // 2
    end_y = int(height * 0.25)
    
    cmd = [ADB_PATH, "shell", "input", "swipe", str(start_x), str(start_y), str(end_x), str(end_y), str(SCROLL_DURATION_MS)]
    subprocess.run(cmd)

def images_are_same(img1, img2):
    # Quick compare of bytes? Or resizing to small thumb?
    if img1 is None or img2 is None: return False
    
    # Simple hash compare or difference
    # Resize to speed up
    t1 = img1.resize((64, 64))
    t2 = img2.resize((64, 64))
    
    # Get difference
    diff = 0
    p1 = list(t1.getdata())
    p2 = list(t2.getdata())
    
    for i in range(len(p1)):
        diff += sum(abs(c1 - c2) for c1, c2 in zip(p1[i], p2[i]))
        
    return diff < 5000 # Threshold

def find_overlap(img1, img2):
    """
    Finds the vertical offset where img1 ends and img2 begins using pixel matching.
    Returns (cut_y1, cut_y2) logic:
    - cut_y1: The row in img1 where we stop (exclusive).
    - cut_y2: The row in img2 where we start (inclusive).
    
    Ideally, img1[cut_y1] matches img2[cut_y2].
    """
    width, height = img1.size
    
    # Define a search strip in img1: Near the bottom, but avoiding the nav bar.
    # Assuming screen height ~2400, Nav bar ~100-150.
    # Let's take a strip 100px high, located 300px from the bottom.
    strip_h = 100
    strip_y_start = height - 400 
    
    if strip_y_start < 0: strip_y_start = height // 2 # Fallback for small images
    
    # Extract the template strip from img1
    # Convert to grayscale for faster comparison
    gray1 = img1.convert("L")
    gray2 = img2.convert("L")
    
    template = gray1.crop((0, strip_y_start, width, strip_y_start + strip_h))
    template_data = list(template.getdata())
    
    # Search for this strip in img2
    # We expect it to be higher up (since we scrolled down, content moved up)
    # So searching from top down.
    # Search range: valid content area. 
    # Don't search at the very top (header) to be safe, but the math should handle it if unique.
    
    best_match_y = -1
    min_diff = float('inf')
    
    # Optimization: Check every 5th row, then refine? Or just check 10% to 80%?
    # Scan range: 
    # If we scrolled 50%, the content moved up by height/2.
    # So the strip at H-400 should now be at H-400 - (Height/2).
    # We search the entire top 3/4 of the image.
    
    search_limit = height - 100
    
    # To speed up in Python without numpy:
    # 1. Compare center column only first.
    # 2. If match, check full strip.
    
    center_x = width // 2
    col_data_template = [template.getpixel((center_x, y)) for y in range(strip_h)]
    col_data_img2 = [gray2.getpixel((center_x, y)) for y in range(search_limit)]
    
    # Brute force scan centered column
    for y in range(0, search_limit - strip_h):
        # Quick check: center pixel of strip vs center pixel of match candidate
        # diff = abs(col_data_img2[y] - col_data_template[0])
        # if diff > 10: continue
        
        # Check full column
        diff = 0
        match = True
        for k in range(0, strip_h, 5): # Check every 5th pixel in column
            if abs(col_data_img2[y+k] - col_data_template[k]) > 20: 
                match = False
                break
        
        if match:
            # Candidate found, check full row/strip rigorously
            # Compare a subsample of the full 2D strip
            curr_diff = 0
            
            # Check 100 random pixels? Or just a grid.
            # Grid: 10x10 points
            for r in range(0, strip_h, 20):
                for c in range(0, width, 50):
                    p1 = template.getpixel((c, r))
                    p2 = gray2.getpixel((c, y + r))
                    curr_diff += abs(p1 - p2)
            
            if curr_diff < min_diff:
                min_diff = curr_diff
                best_match_y = y
                
    # Threshold for "Good Match"
    # If min_diff is too high, correlation failed (screen changed completely or hit bottom)
    if min_diff > 5000: # Arbitrary heuristic
        print(f"  No good overlap found (Diff: {min_diff}). Assuming end or disjoint.")
        return None
        
    print(f"  Overlap found! img1 row {strip_y_start} matches img2 row {best_match_y}. Diff: {min_diff}")
    
    # Cut points
    # We keep img1 up to strip_y_start
    # We keep img2 from best_match_y
    return (strip_y_start, best_match_y)

def stitch_smart(images):
    if not images: return None
    
    # Start with the first image
    # Note: We probably want to crop the Footer (Nav bar) from the last pasted block
    # and the Header (Status bar) from the next block.
    # Our find_overlap returns specific cut points that inherently handle this 
    # IF the template strip was chosen well (between header and footer).
    
    # HOWEVER, for the very first image, we keep the Header.
    # For the very last image, we keep the Footer.
    
    canvas = images[0]
    
    for i in range(1, len(images)):
        prev_img = canvas
        curr_img = images[i]
        
        res = find_overlap(images[i-1], curr_img) # Compare with original unstitched prev to find offset
        
        if res is None:
            # Just append? Or stop?
            print(f"  Could not stitch page {i+1}. Stopping.")
            break
            
        cut_y1, cut_y2 = res
        
        # Logic: 
        # img1's content matches img2 at specific rows.
        # We want to keep img1 up to cut_y1.
        # And start img2 at cut_y2.
        
        # But wait, 'canvas' is growing. We can't use 'prev_img' as canvas directly for matching.
        # We matched images[i-1] vs images[i].
        # images[i-1] is the *bottom* stored in the canvas.
        
        # Implementation:
        # 1. Crop canvas to remove the footer of the last addition (which goes beyond cut_y1).
        #    Actually, we just need to backtrack the canvas height?
        #    Matching tells us: The content at 'cut_y1' in the OLD image is the seam.
        #    So we should slice the **last added image** at 'cut_y1'.
        
        # Let's reconstruct.
        # Instead of growing canvas, let's collect cropped segments.
        pass
        
    # Re-do: Collect segments
    segments = []
    
def safe_crop(img, left, top, right, bottom):
    """Safely crop an image, ensuring coordinates are within bounds."""
    w, h = img.size
    left = min(max(0, left), w)
    top = min(max(0, top), h)
    right = min(max(0, right), w)
    bottom = min(max(0, bottom), h)
    
    if left >= right or top >= bottom:
        return None # Empty crop
        
    return img.crop((left, top, right, bottom))

def stitch_smart(images):
    if not images: return None
    if len(images) == 1: return images[0]
    
    segments = []
    last_cut_start = 0 
    
    # Process pairs
    for i in range(len(images) - 1):
        # Current image is images[i]
        # Next match will determine where images[i] ends
        # And where images[i+1] starts (for next iteration)
        
        next_img = images[i+1]
        
        # Determine overlap
        res = find_overlap(images[i], next_img)
        
        cut_point_in_current = images[i].height # Default: keep all if no overlap
        next_start_in_next = 0    # Default: start fresh if no overlap
        
        if res:
            cut_point_in_current, next_start_in_next = res
            
        # Add segment from current image
        # From 'last_cut_start' (start of valid content in this img)
        # To 'cut_point_in_current' (exclusive end of content in this img)
        
        # Validate logic:
        # If cut_point_in_current is ABOVE last_cut_start, we have negative content?
        # This shouldn't happen unless detection is wrong.
        
        if cut_point_in_current > last_cut_start:
            seg = safe_crop(images[i], 0, last_cut_start, images[i].width, cut_point_in_current)
            if seg: segments.append(seg)
        
        # Prepare for next iteration
        last_cut_start = next_start_in_next
        
    # Append the final image remainder
    last_img = images[-1]
    if last_cut_start < last_img.height:
        seg = safe_crop(last_img, 0, last_cut_start, last_img.width, last_img.height)
        if seg: segments.append(seg)
        
    if not segments: return None
    
    # Create final image
    total_h = sum(s.height for s in segments)
    if total_h == 0: return None
    
    final_im = Image.new('RGB', (images[0].width, total_h))
    
    y = 0
    for s in segments:
        final_im.paste(s, (0, y))
        y += s.height
        
    return final_im

def main():
    if not os.path.exists(SAVE_DIR):
        os.makedirs(SAVE_DIR)
        
    width, height = get_screen_size()
    print(f"Screen size: {width}x{height}")
    
    captured_images = []
    
    print("Starting capture...")
    
    for i in range(MAX_PAGES):
        print(f"Capturing page {i+1}...")
        img = capture_screenshot()
        
        if img is None:
            print("Failed to capture image.")
            break
            
        # Check if same as previous (end of scroll)
        if captured_images and images_are_same(captured_images[-1], img):
            print("End of content reached.")
            break
            
        captured_images.append(img)
        
        print("Scrolling...")
        scroll_down(width, height)
        # Increased wait time for stability
        time.sleep(2.0) 
        
    if not captured_images:
        print("No images captured.")
        return
        
    print(f"Stitching {len(captured_images)} pages...")
    timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"long_screenshot_{timestamp}.png"
    filepath = os.path.join(SAVE_DIR, filename)
    
    # Stitch
    try:
        final_image = stitch_smart(captured_images)
        if final_image:
            final_image.save(filepath)
            print(f"Saved to {filepath}")
            os.startfile(filepath)
        else:
             print("Stitching failed.")
    except Exception as e:
        print(f"Stitching error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
