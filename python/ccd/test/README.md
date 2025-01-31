# LOCI Python Test Programs

This directory contains a series of python test programs, that can invoke the CCD Flask API. These were written whilst developing the LOCI Java layer, and basically mimic the Java test programs.

## Installation

They can be installed on a user's system, inside a Python virtual environment, as follows:

* Install python virtual environment: **sudo apt-get install python3-venv**
* Setup the virtual environment: **python3 -m venv .venv**
* Activate the virtual environment (for bash shell): **source .venv/bin/activate
* Activate the virtual environment (for tcsh shell): **source .venv/bin/activate.csh**
* Install the depenencies for the command line program in the python virtual environment:
  *  **pip install Flask**
  *  **pip install requests**

## Useage

To invoke the CCD Flask API end-point, you need to know it's IP address. On the ARI LAN this is 150.204.240.135. The CCD Flask API end-point currently sits on port 5100.

All the command-line test routines have a '--help' option with details on the options available.

*  **set hostname = "150.204.240.135"**

*  **python3 test_set_image_dimensions.py --ip_address ${hostname} --port_number 5100 --xbin 2 --ybin 2** Set the camera binning. A optional sub-window can also be specified:
    *  **python3 test_set_image_dimensions.py --ip_address ${hostname} --port_number 5100 --xbin 2 --ybin 2 --start_x 1 --start_y 1 --end_x 100 --end_y 100**
*  **python3 test_take_bias_frame.py --ip_address ${hostname} --port_number 5100 --multrun start** Take a bias frame. The multrun option is optional (but defaults to start).
*  **python3 test_take_dark_frame.py --ip_address ${hostname} --port_number 5100 --exposure_time 10.0 --multrun start** Take a dark frame of the specified length (--exposure_length is in decimal seconds). The multrun option is optional (but defaults to start).
*  **python3 test_take_exposure.py --ip_address ${hostname} --port_number 5100 --exposure_time 10.0 --multrun start --exposure_type exposure** Take an exposure of the specified length (--exposure_length is in decimal seconds). The multrun option is optional (but defaults to start). The exposure_type option is optional  (but defaults to exposure).
*  **python3 test_abort_exposure.py --ip_address ${hostname} --port_number 5100** Abort a currently running exposure/bias/dark.
*  **python3 test_get_temperature.py --ip_address ${hostname} --port_number 5100** Get the detector temperature.
*  **python3 test_set_temperature.py --ip_address ${hostname} --port_number 5100 --temperature -14** Set the detector temperature.
*  **python3 test_get_camera_status.py --ip_address ${hostname} --port_number 5100** Get the camera status (is it DRV_IDLE or DRV_ACQUIRING).
*  **python3 test_get_exposure_progress.py --ip_address ${hostname} --port_number 5100** Get the progress of an ongoing exposure (exposure length, elapsed and remaining exposure length).
*  **python3 test_set_cooling.py --ip_address ${hostname} --port_number 5100 --cooling true** Turn the camera cooling on or off.
*  **python3 test_clear_header_keywords.py --ip_address ${hostname} --port_number 5100** Clear the user-defined FITS headers list.
*  **python3 test_set_header_keyword.py --ip_address ${hostname} --port_number 5100 --keyword TEST1 --value_string "Test FITS value." --comment "This is a test." --units "N/A"** Add a user-defined FITS header. --comment and --units are optional. There are alternate --value_int , --value_bool and --value_double arguments to add FITS headers of different types.

## Test server

There is a fake Flask CCD endpoint that can be invoked as follows:

**python3 testmain.py**

This exposes an outdated version of the CCD Flask API, and is therefore deprecated.

