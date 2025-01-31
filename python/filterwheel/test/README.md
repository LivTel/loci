# LOCI Python Filter Wheel Test Programs

This directory contains a series of python test programs, that can invoke the Filter Wheel Flask API. These basically mimic the Java test programs.

## Installation

They can be installed on a user's system, inside a Python virtual environment, as follows:

* Install python virtual environment: **sudo apt-get install python3-venv**
* Setup the virtual environment: **python3 -m venv .venv**
* Activate the virtual environment (for bash shell): **source .venv/bin/activate**
* Activate the virtual environment (for tcsh shell): **source .venv/bin/activate.csh**
* Install the depenencies for the command line program in the python virtual environment:
  *  **pip install Flask**
  *  **pip install requests**

## Useage

To invoke the Filter Wheel Flask API end-point, you need to know it's IP address. On the ARI LAN this is 150.204.240.135. The Filter Wheel Flask API end-point currently sits on port 5101.

All the command-line test routines have a '--help' option with details on the options available.

*  **set hostname = "150.204.240.135"**

*  **test_get_filter_position.py --ip_address ${hostname} --port_number 5101** Get the current position of the filter wheel.
*  **test_get_status.py --ip_address ${hostname} --port_number 5101** Get the current position of the filter wheel.
*  **test_set_filter_position_by_name.py --ip_address ${hostname} --port_number 5101 --filter_name SDSS-R** Set the current position of the filter wheel by moving the specified  filter into the beam.
*  **test_set_filter_position.py --ip_address ${hostname} --port_number 5101 --filter_position 0** Set the current position of the filter wheel by moving the specified filter position into the beam (1..5).
