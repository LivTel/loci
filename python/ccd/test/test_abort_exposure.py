#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_abort_exposure.py',
                    description='Abort/stop an already running exposure by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.129', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5100, help='The port number the server is running on.')
args = parser.parse_args()

# Composing a payload for API
#payload = {'filename' : str(args.filename), 'exposure_time' : args.exposure_time }
# Defining content type for our payload
#headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/abortExposure"
print ("Invoking end-point: ", urlname, ".")
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname)
# Printing out the response of API
print(response.text)
