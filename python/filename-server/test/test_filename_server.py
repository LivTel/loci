#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_filename_server.py',
                    description='Invoke the filter server API to return an LT  filename.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.129', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=3000, help='The port number the server is running on.')
parser.add_argument('-t', '--instrument', type=str, default='LOCI', help='The name or instrument code of the instrument: one of: FRODOSPEC_BLUE	| RINGO2 | O | I | LOCI | RISE | FRODOSPEC_RED | MOPTOP_1 | MOPTOP_2 | MOPTOP_3 | MOPTOP_4')
parser.add_argument('-e', '--exposure', type=str, default='exposure', help='The name or exposure code (the type of data in the file), one of: arc|bias|dark|exposure|sky-flat|acquire|standard|lamp-flat.')
parser.add_argument('-m', '--multrun', type=str, default='start', help='Whether this filename is the start of a new multrun, or the next run in the multrun, one of: start | next.')
parser.add_argument('-x', '--extension', type=str, default='fits', help='The filename extension of this filename.')


args = parser.parse_args()

# Composing a payload for API
payload = {'instrument' : args.instrument, 'exposure' : args.exposure, 'multrun' : args.multrun, 'extension' : args.extension }
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/filename"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
