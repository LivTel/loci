#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# special type for parsing boolean arguments
def str2bool(v):
    if isinstance(v, bool):
        return v
    if v.lower() in ('yes', 'true', 't', 'y', '1'):
        return True
    elif v.lower() in ('no', 'false',  'f', 'n', '0'):
        return False
    else:
        raise argparse.ArgumentTypeError('Boolean value expected.')

# command line arguments
parser = argparse.ArgumentParser(prog='test_set_cooling.py',
                    description='Set whether to turn the detector cooling on or off by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.135', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5100, help='The port number the server is running on.')
parser.add_argument('-c', '--cooling', type=str2bool, help='Whether to turn the detector cooling on (true) or off (false).')


args = parser.parse_args()

# Composing a payload for API
payload = {'cooling' : args.cooling }
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/setCooling"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
