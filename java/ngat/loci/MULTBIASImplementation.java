// MULTBIASImplementation.java
// $Id$
package ngat.loci;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTBIAS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: MULTBIASImplementation.java $
 * @see ngat.loci.CALIBRATEImplementation
 */
public class MULTBIASImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public MULTBIASImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTBIAS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTBIAS";
	}

	/**
	 * This method returns the MULTBIAS command's acknowledge time. 
         * <ul>
         * <li>We construct an ACK object instance.
	 * <li>The acknowledge time is set to the maximum readout time
	 *     (retrieved from the Loci status object) plus the server conenction thread's default acknowledge time.
         * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #status
	 * @see LociStatus#getMaxReadoutTime
	 * @see #serverConnectionThread
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(status.getMaxReadoutTime()+
			serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTBIAS command. 
	 * <ul>
	 * <li>We initialise the status objects exposure status (setExposureCount / setExposureNumber).
	 * <li>It moves the fold mirror to the correct location.
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the CCD Flask API.
	 * <li>setFilterWheelFitsHeaders is called to get the current filter wheel position, and set some FITS headers based on this.
	 * <li>For each exposure it performs the following:
	 *	<ul>
	 *      <li>We call setPerFrameFitsHeaders to set the per-frame FITS headers.
	 *      <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). 
	 *          These are sent on to the CCD Flask API.
	 * 	<li>It performs an exposure by calling sendTakeBiasFrameCommand.
	 * 	<li>We update the status object (setExposureNumber);
	 *      <li>We send a FILENAME_ACK to the client updating them with the returned filename, 
	 *          and keeping the connection open.
	 * 	<li>Keeps track of the generated filenames in the list.
	 * 	</ul>
	 * <li>It sets up the return values to return to the client.
	 * </ul>
	 * @see #sendTakeBiasFrameCommand
	 * @see ngat.loci.LociStatus#setExposureCount
	 * @see ngat.loci.LociStatus#setExposureNumber
	 * @see ngat.loci.CommandImplementation#testAbort
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFilterWheelFitsHeaders
	 * @see ngat.loci.HardwareImplementation#getFitsHeadersFromISS
	 * @see ngat.loci.HardwareImplementation#setPerFrameFitsHeaders
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTBIAS multBiasCommand = (MULTBIAS)command;
		MULTBIAS_DONE multBiasDone = new MULTBIAS_DONE(command.getId());
		FILENAME_ACK filenameAck = null;
		List reduceFilenameList = null;
		String filename = null;
		int exposureCount,index;
		
		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multBiasCommand,multBiasDone) == true)
			return multBiasDone;
		// get multbias data
		exposureCount = multBiasCommand.getNumberExposures();
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:exposureCount = "+exposureCount+".");
	// setup exposure status.
		status.setExposureCount(exposureCount);
		status.setExposureNumber(0);
		// initial FITS headers setup
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			multBiasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2600);
			multBiasDone.setErrorString(this.getClass().getName()+
						    ":processCommand:clearFitsHeaders failed:"+e);
			multBiasDone.setSuccessful(false);
			return multBiasDone;
		}			
		if(setFitsHeaders(multBiasCommand,multBiasDone) == false)
			return multBiasDone;
		if(setFilterWheelFitsHeaders(multBiasCommand,multBiasDone) == false)
			return multBiasDone;
	// do bias frames
		index = 0;
		reduceFilenameList = new Vector();
		while(index < multBiasCommand.getNumberExposures())
		{
			// setup per-frame FITS headers
			if(setPerFrameFitsHeaders(multBiasCommand,multBiasDone,FitsHeaderDefaults.OBSTYPE_VALUE_BIAS,0,
						  multBiasCommand.getNumberExposures(),index+1) == false)
				return multBiasDone;
			// update ISS FITS headers
			if(getFitsHeadersFromISS(multBiasCommand,multBiasDone) == false)
				return multBiasDone;
			if(testAbort(multBiasCommand,multBiasDone) == true)
				return multBiasDone;
			// do bias
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				 ":processCommand:Starting sendTakeBiasFrameCommand.");
			try
			{
				filename = sendTakeBiasFrameCommand();
			}
			catch(Exception e )
			{
				loci.error(this.getClass().getName()+":processCommand:sendTakeBiasFrameCommand failed:",e);
				multBiasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2601);
				multBiasDone.setErrorString(this.getClass().getName()+
							   ":processCommand:sendTakeBiasFrameCommand failed:"+e);
				multBiasDone.setSuccessful(false);
				return multBiasDone;
			}
			if(testAbort(multBiasCommand,multBiasDone) == true)
				return multBiasDone;
			// update status
			status.setExposureNumber(index+1);			
			status.setExposureFilename(filename);
		// send acknowledge to say frame is completed.
			filenameAck = new FILENAME_ACK(multBiasCommand.getId());
			filenameAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime()+
						      status.getMaxReadoutTime());
			filenameAck.setFilename(filename);
			try
			{
				serverConnectionThread.sendAcknowledge(filenameAck);
			}
			catch(IOException e)
			{
				loci.error(this.getClass().getName()+
					   ":processCommand:sendAcknowledge:"+command+":"+e.toString(),e);
				multBiasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2602);
				multBiasDone.setErrorString(e.toString());
				multBiasDone.setSuccessful(false);
				return multBiasDone;
			}
		// add filename to list for data pipeline processing.
			reduceFilenameList.add(filename);
		// test whether an abort has occured.
			if(testAbort(multBiasCommand,multBiasDone) == true)
				return multBiasDone;
			index++;
		}
	// return done object.
		multBiasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		multBiasDone.setErrorString("");
		multBiasDone.setSuccessful(true);
		return multBiasDone;
	}
}
