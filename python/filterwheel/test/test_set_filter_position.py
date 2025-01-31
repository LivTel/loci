#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_set_filter_position.py',
                    description='Move the filter wheel so the specified filter position is in the beam by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.135', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5101, help='The port number the server is running on.')
parser.add_argument('-f', '--filter_position', type=int, help='The position of the filter to move into the beam, (1..5).')

args = parser.parse_args()

# Composing a payload for API
payload = {'filter_position' : args.filter_position }
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/setFilterPosition"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a POST request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
