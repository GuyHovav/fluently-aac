import requests
import json

words = ['SUV', 'Oil', 'mechanic', 'seatbelt', 'acceleration']

print("--- DIAGNOSTICS START ---")
for w in words:
    print(f"Testing: {w}")
    # Arasaac
    a_url = f"https://api.arasaac.org/v1/pictograms/en/search/{w.lower()}"
    a_resp = requests.get(a_url)
    print(f"  Arasaac: {a_resp.status_code}, data: {str(a_resp.json())[:50]}")
    
    # GlobalSymbols
    g_url = f"https://globalsymbols.com/api/v1/concepts/suggest?query={w}&language=eng"
    g_resp = requests.get(g_url)
    print(f"  GS: {g_resp.status_code}, data: {str(g_resp.json())[:50]}")
print("--- DIAGNOSTICS END ---")
