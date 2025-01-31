#!/usr/bin/python3
# Importing required packages
import argparse
import requests
import json

# command line arguments
parser = argparse.ArgumentParser(prog='test_set_image_dimensions.py',
                    description='Set the detector window/binning by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.135', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5100, help='The port number the server is running on.')
parser.add_argument('-x', '--xbin', type=int, default=1, help='The X binning factor.')
parser.add_argument('-y', '--ybin', type=int, default=1, help='The Y binning factor.')
parser.add_argument('-xs', '--start_x', type=int, default=1, help='The X start position of the sub-window, in (unbinned?) pixels.')
parser.add_argument('-ys', '--start_y', type=int, default=1, help='The Y start position of the sub-window, in (unbinned?) pixels.')
parser.add_argument('-xe', '--end_x', type=int, default=2048, help='The X end position of the sub-window, in (unbinned?) pixels.')
parser.add_argument('-ye', '--end_y', type=int, default=2048, help='The Y end position of the sub-window, in (unbinned?) pixels.')

args = parser.parse_args()

# Composing a payload for API
payload = {'horizontal_binning' : args.xbin, 'vertical_binning' : args.ybin, 'horizontal_start' : args.start_x,
           'vertical_start' : args.start_y, 'horizontal_end' : args.end_x, 'vertical_end' : args.end_y,}
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/setImageDimensions"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
