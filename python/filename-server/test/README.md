# filename-server Python Test Programs

This directory contains a series of python test programs, that can invoke the filename-server API.
The filename-server software is described here: https://gitlab.services.newrobotictelescope.org/nrt-crew/ljmu/filename-server/

You can build a docker image of the filename-server as follows:

```
git clone https://gitlab.services.newrobotictelescope.org/nrt-crew/ljmu/filename-server/
cd filename-server
docker build -f containers/Dockerfile -t filename_server_image .
docker save -o filename_server_image.tar filename_server_image
gzip filename_server_image.tar
```

This can be installed on a host as follows:

```
docker load -i filename_server_image.tar
docker run -p 80:80 -p 443:443 -p 3000:3000 --name=filename-server -it -d --restart unless-stopped filename_server_image
```

Pointing a browser at http://localhost:3000/ will then show a webpage describing the protocol these programs are trying to test.


## Installation

They can be installed on a user's system, inside a Python virtual environment, as follows:

* Install python virtual environment: **sudo apt-get install python3-venv**
* Setup the virtual environment: **python3 -m venv .venv**
* Activate the virtual environment (for bash shell): **source .venv/bin/activate**
* Activate the virtual environment (for tcsh shell): **source .venv/bin/activate.csh**
* Install the depenencies for the command line program in the python virtual environment:
  *  **pip install requests**
  
## Useage

To invoke the filename-server API end-point, you need to know it's IP address.

All the command-line test routines have a '--help' option with details on the options available.

*  **set hostname = "150.204.240.135"**

*  **python3 test_filename_server.py --ip_address ${hostname} --port_number 80 --instrument <name/code> --exposure < exposurecode> --multrun <start|next> --extension <fits>** 

