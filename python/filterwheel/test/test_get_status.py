#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_get_status.py',
                    description='Get the current (connection) status of the filter wheel by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.135', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5101, help='The port number the server is running on.')
args = parser.parse_args()

# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/getStatus"
print ("Invoking end-point: ", urlname, ".")
# Sending a GET request to the server (API) and receiving a reply
response = requests.get(url=urlname)
# Printing out the response of API
print(response.text)
