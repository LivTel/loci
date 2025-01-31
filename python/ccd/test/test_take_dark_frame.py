#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_take_dark_frame.py',
                    description='Take a dark frame by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.129', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5100, help='The port number the server is running on.')
parser.add_argument('-e', '--exposure_time', type=float, default=1.0, help='The dark exposure length in decimal seconds.')
parser.add_argument('-m', '--multrun', type=str, help='An optional parameter to control whether the generated dark filename is the start of a new multrun, or the next frame in the current multrun. Specify one of: [start|next].')

args = parser.parse_args()

# Composing a payload for API
payload = {'exposure_time' : args.exposure_time }
if args.multrun is not None:
    payload["multrun"] = str(args.multrun)
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/takeDarkFrame"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
