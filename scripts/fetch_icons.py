import requests
import json
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def search(term):
    try:
        response = requests.get(f"https://api.arasaac.org/v1/pictograms/en/search/{term}", verify=False)
        data = response.json()
        if data and len(data) > 0:
            # Prefer non-color specific if possible, but just take first
            first = data[0]
            print(f"{term}: {first['_id']}")
        else:
            print(f"{term}: Not Found")
    except Exception as e:
        print(f"{term}: Error {e}")

search("home")
search("food")
search("feelings")
search("school") # for Learn
