/* test_andor_readout_speeds.c
** $Id$
 */
/**
 * Low level Andor test program to print out the supported horizontal and vertical readout speeds.
 * <pre>
 * </pre>
 * @author $Author$
 * @version $Revision$
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "atmcdLXd.h"

/* hash definitions */
/**
 * Truth value.
 */
#define TRUE 1
/**
 * Falsity value.
 */
#define FALSE 0
/* internal variables */
/**
 * Revision control system identifier.
 */
static char rcsid[] = "$Id: test_andor_exposure.c,v 1.1 2010/10/07 13:22:41 eng Exp $";
/**
 * How many Andor cameras are detected.
 */
static at_32 Number_Of_Cameras = 1;
/**
 * Which camera to use.
 */
static int Selected_Camera = -1;
/**
 * Filename for configuration directory. 
 */
static char *Config_Dir = "/usr/local/etc/andor";
/* internal routines */
static int Parse_Arguments(int argc, char *argv[]);
static void Help(void);

/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 */
int main(int argc, char *argv[])
{
	unsigned long andor_retval;
	at_32 Camera_Handle;
	int i,preamp_gain_count;
	float gain;
	
	GetAvailableCameras(&Number_Of_Cameras);
	fprintf(stdout,"Found %d cameras.\n",Number_Of_Cameras);
/* parse arguments */
	fprintf(stdout,"Parsing Arguments.\n");
	if(!Parse_Arguments(argc,argv))
		return 1;
	if((Selected_Camera >= 0)&&(Selected_Camera <= Number_Of_Cameras))
	{
		fprintf(stdout,"GetCameraHandle(Selected_Camera=%d)\n",Selected_Camera);
		GetCameraHandle(Selected_Camera, &Camera_Handle);
		fprintf(stdout,"SetCurrentCamera(Camera_Handle=%d)\n",Camera_Handle);
		SetCurrentCamera(Camera_Handle);
	}
	fprintf(stdout,"Initialize(%s)\n",Config_Dir);
	andor_retval = Initialize(Config_Dir);
	if(andor_retval!=DRV_SUCCESS)
	{
		fprintf(stderr,"Initialize failed %lu.\n",andor_retval);
		return 2;
	}
	fprintf(stdout,"sleep(2)\n");
	sleep(2);
	fprintf(stdout,"SetReadMode(4) (image)\n");
	andor_retval = SetReadMode(4);
	if(andor_retval!=DRV_SUCCESS)
	{
		fprintf(stderr,"SetReadMode failed %lu.\n",andor_retval);
		return 3;
	}
	fprintf(stdout,"SetAcquisitionMode(1) (single scan)\n");
	andor_retval = SetAcquisitionMode(1);
	if(andor_retval!=DRV_SUCCESS)
	{
		fprintf(stderr,"SetAcquisitionMode failed %lu.\n",andor_retval);
		return 2;
	}
	/* get number of preamp gains */
	andor_retval = GetNumberPreAmpGains(&preamp_gain_count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberPreAmpGains() failed (%lu).",andor_retval);
		return 4;
	}
	fprintf(stdout,"GetNumberPreAmpGains() returned %d gains.\n",preamp_gain_count);
	for(i=0;i < preamp_gain_count; i++)
	{
		andor_retval = GetPreAmpGain(i,&gain);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"GetPreAmpGain(%d) failed (%lu).",i,andor_retval);
			return 5;
		}
		fprintf(stdout,"GetPreAmpGain(index=%d) returned preamp gain %.2f.\n",i,gain);
	}
/* close  */
	fprintf(stdout,"ShutDown()\n");
	ShutDown();
	return 0;
}

/* -----------------------------------------------------------------------------
**      Internal routines
** ----------------------------------------------------------------------------- */
/**
 * Help routine.
 */
static void Help(void)
{
	fprintf(stdout,"Test Andor Readout Speed:Help.\n");
	fprintf(stdout,"This program prints out the supported horizontal and vertical readout speeds..\n");
	fprintf(stdout,"test_andor_readout_speeds \n");
	fprintf(stdout,"\t[-camera <n>]\n");
	fprintf(stdout,"\t[-co[nfig_dir] <directory>]\n");
	fprintf(stdout,"\t[-h[elp]]\n");
	fprintf(stdout,"\n");
	fprintf(stdout,"\t-help prints out this message and stops the program.\n");
	fprintf(stdout,"\n");
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see #Selected_Camera
 * @see #Config_Dir
 */
static int Parse_Arguments(int argc, char *argv[])
{
	int i,retval,log_level;

	for(i=1;i<argc;i++)
	{
		if((strcmp(argv[i],"-camera")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Selected_Camera);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing selected camera %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:camera requires a camera number.\n");
				return FALSE;
			}

		}
		else if((strcmp(argv[i],"-config_dir")==0)||(strcmp(argv[i],"-co")==0))
		{
			if((i+1)<argc)
			{
				Config_Dir = argv[i+1];
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:config dir name required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-help")==0)||(strcmp(argv[i],"-h")==0))
		{
			Help();
			exit(0);
		}
		else
		{
			fprintf(stderr,"Parse_Arguments:argument '%s' not recognized.\n",argv[i]);
			return FALSE;
		}
	}
	return TRUE;
}


