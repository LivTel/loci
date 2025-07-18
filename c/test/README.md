# LOCI c/test Program directory

This directory contains some low level C programs, that test the camera head by invoking the Andor SDK C API.

* **test_andor_readout_speeds.c** Prints out the horizontal and vertical readout speeds.
* **test_andor_preamp_gains.c** Prints out the available pre-amp gains on the camera.


## Deployment / Installation

Currently this software is manually deployed to the loci1 machine as follows

* Copy the binary, environment.csh and Andor C library (theres a copy in **ltdevsrv:/home/dev/src/andor/andor-2.104.30000/lib/libandor-x86_64.so.2.104.30000.0** ) to a bin directory on your target machine.
* Copy the Andor SDK config files into a sub-directory of your target directory e.g. **scp -Cr ~dev/src/andor/andor-2.104.30000/etc cmottram@loci1:bin**
* Log into loci1
* **sudo tcsh** You need root access to talk to the camera.
* **ln -s libandor-x86_64.so.2.104.30000.0 libandor.so.2**
* **source environment.csh** This sets the LD_LIBRARY_PATH.
* Invoke the binary, setting the config directory as necessary: **./test_andor_readout_speeds -config_dir /home/cmottram/bin/etc/**
