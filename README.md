# LOCI

Source code for the Liverpool Telescope / Java for the Liverpool Optical Compact Imager (LOCI) instrument.

This is a simple imaging camera (Andor Ikon-L 936 CCD camera) ,with a Starlight Express filter wheel. It is designed to be used on both the Liverpool Telescope (LT) and the New Robotic Telescope (NRT), and the software infrastructure is designed to enable as much common code between the two installations as possible.

This repository currently contains the upper robotic Java layer that interfaces with the Liverpool Telescope RCS.

The lower-level CCD control software API is provided by the NRT, and their repository is currently located here:

https://gitlab.services.newrobotictelescope.org/nrt-crew/ljmu/loci-ctrl/

Current details for the test system in the lab are in LT wikiword: LOCIControlComputer

## Directory Structure

* **java** This contains the source code for the Liverpool Telescope robotic layer, which receives commands from the LT robotic control system.

The Makefile.common file is included in Makefile's to provide common root directory information.

## Dependencies / Prerequisites

* The ngat repo/package must be installed: https://github.com/LivTel/ngat
* The software can only be built from within an LT development environment

* The ics_gui package should be installed on the machine you wish to control the instrument from (in it's LT form): https://github.com/LivTel/ics_gui
