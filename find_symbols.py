import requests
import json
import urllib3
import sys
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

terms = ["family", "friends", "need", "you", "apps", "tablet", "computer", "want"]

with open('symbols_utf8.txt', 'w', encoding='utf-8') as f:
    def search(term):
        f.write(f"\n--- Searching for: {term} ---\n")
        try:
            response = requests.get(f"https://api.arasaac.org/v1/pictograms/en/search/{term}", verify=False)
            data = response.json()
            if data:
                for i, item in enumerate(data[:10]): # Top 10
                    keywords = [k['keyword'] for k in item.get('keywords', [])]
                    f.write(f"Option {i+1}: ID={item['_id']}, Keywords={keywords}\n")
            else:
                f.write(f"{term}: Not Found\n")
        except Exception as e:
            f.write(f"{term}: Error {e}\n")

    for term in terms:
        search(term)
