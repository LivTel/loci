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
parser = argparse.ArgumentParser(prog='test_set_header_keyword.py',
                                 description='Set a user-defined FITS header keyword/value pair, to be written into the next generated FITS image, by sending a request to the API server.')
parser.add_argument('-i', '--ip_address', type=str, default='150.204.240.129', help='The IP address the server is running on.')
parser.add_argument('-p', '--port_number', type=int, default=5100, help='The port number the server is running on.')
parser.add_argument('-k', '--keyword', type=str, help='The keyword name.')
parser.add_argument('-vs', '--value_string', type=str, help='The keyword value (as a string).')
parser.add_argument('-vi', '--value_int', type=int, help='The keyword value (as an integer).')
parser.add_argument('-vd', '--value_double', type=float, help='The keyword value (as a float).')
parser.add_argument('-vb', '--value_bool', type=str2bool, help='The keyword value (as a boolean).')
parser.add_argument('-c', '--comment', type=str, help='An optional comment string.')
parser.add_argument('-u', '--units', type=str, help='An optional units string.')
args = parser.parse_args()

# Composing a payload for API
payload = {'keyword' : args.keyword }
if args.value_string is not None:
    payload.update({'value': args.value_string})
if args.value_int is not None:
    payload.update({'value': args.value_int})
if args.value_double is not None:
    payload.update({'value': args.value_double})
if args.value_bool is not None:
    payload.update({'value': args.value_bool})
if args.comment is not None:
    payload.update({'comment': args.comment})
if args.units is not None:
    payload.update({'units': args.units})
# Defining content type for our payload
headers = {'Content-type': 'application/json'}
# Compose the API endpoint URL
urlname = "http://" + str(args.ip_address) + ":" + str(args.port_number) + "/setHeaderKeyword"
print ("Invoking end-point: ", urlname, " with payload:", json.dumps(payload))
# Sending a post request to the server (API) and receiving a reply
response = requests.post(url=urlname , data=json.dumps(payload), headers=headers)
# Printing out the response of API
print(response.text)
