import json
import sys

import requests
from requests.auth import HTTPBasicAuth


# Asks Watson a question using the given credentials
#
# argument 1: username
# argument 2: password
# argument 3: question
def main():
    response = ask_question(sys.argv[1], sys.argv[2], sys.argv[3])
    print(json.dumps(json.loads(response.content), indent=4))


# Returns an HTTP response after asking Watson the given question using the given credentials
def ask_question(username, password, question):
    authentication = HTTPBasicAuth(username, password)
    headers = {'X-SyncTimeout': '30', 'Accept': 'application/json', 'Content-Type': 'application/json'}
    data = {'question': {'questionText': question}}

    response = requests.post('https://watson-wdc01.ihost.com/instance/540/deepqa/v1/question',
                             data=json.dumps(data),
                             auth=authentication,
                             headers=headers)

    return response


main()
