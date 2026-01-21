import requests
import json
import urllib3
import sys
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

terms = ["I", "You", "We", "They", "He", "She", "Want", "Need", "Like", "Feel", "Have", "Go", "What", "Where", "When", "Why"]

with open('symbols_utf8.txt', 'w', encoding='utf-8') as f:
    def search(term):
        f.write(f"\n--- Searching for: {term} ---\n")
        try:
            response = requests.get(f"https://api.arasaac.org/v1/pictograms/en/search/{term}", verify=False)
            data = response.json()
            if data:
                for i, item in enumerate(data[:5]): # Top 5
                    keywords = [k['keyword'] for k in item.get('keywords', [])]
                    f.write(f"Option {i+1}: ID={item['_id']}, Keywords={keywords}\n")
            else:
                f.write(f"{term}: Not Found\n")
        except Exception as e:
            f.write(f"{term}: Error {e}\n")

    for term in terms:
        search(term)
