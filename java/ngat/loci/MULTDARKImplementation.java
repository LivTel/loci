// MULTDARKImplementation.java
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
 * This class provides the implementation for the MULTDARK command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: MULTDARKImplementation.java $
 * @see ngat.loci.CALIBRATEImplementation
 */
public class MULTDARKImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public MULTDARKImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTDARK&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTDARK";
	}

	/**
	 * This method returns the MULTDARK command's acknowledge time. 
         * <ul>
         * <li>We construct an ACK object instance.
	 * <li>The acknowledge time is set to the MULTDARK exposure length plus the maximum readout time
	 *     (retrieved from the Loci status object) plus the server connection thread's default acknowledge time.
         * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #status
	 * @see LociStatus#getMaxReadoutTime
	 * @see #serverConnectionThread
	 * @see MULTDARK#getExposureTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTDARK multDarkCommand = (MULTDARK)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(multDarkCommand.getExposureTime()+status.getMaxReadoutTime()+
			serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}
	
	/**
	 * This method implements the MULTDARK command. 
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
	 * 	<li>It performs a dark frame exposure by calling sendTakeDarkFrameCommand.
	 * 	<li>We update the status object (setExposureNumber);
	 *      <li>We send a FILENAME_ACK to the client updating them with the returned filename, 
	 *          and keeping the connection open.
	 * 	<li>Keeps track of the generated filenames in the list.
	 * 	</ul>
	 * <li>It sets up the return values to return to the client.
	 * </ul>
	 * @see ngat.loci.LociStatus#setExposureCount
	 * @see ngat.loci.LociStatus#setExposureNumber
	 * @see ngat.loci.CALIBRATEImplementation#sendTakeDarkFrameCommand
	 * @see ngat.loci.CALIBRATEImplementation#reduceCalibrate
	 * @see ngat.loci.CommandImplementation#testAbort
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFilterWheelFitsHeaders
	 * @see ngat.loci.HardwareImplementation#getFitsHeadersFromISS
	 * @see ngat.loci.HardwareImplementation#setPerFrameFitsHeaders
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTDARK multDarkCommand = (MULTDARK)command;
		MULTDARK_DONE multDarkDone = new MULTDARK_DONE(command.getId());
		FILENAME_ACK filenameAck = null;
		CALIBRATE_DP_ACK calibrateDpAck = null;
		List reduceFilenameList = null;
		String filename = null;
		int exposureCount,index;
		
		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multDarkCommand,multDarkDone) == true)
			return multDarkDone;
		// get multdark data
		exposureCount = multDarkCommand.getNumberExposures();
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
			multDarkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2700);
			multDarkDone.setErrorString(this.getClass().getName()+
						    ":processCommand:clearFitsHeaders failed:"+e);
			multDarkDone.setSuccessful(false);
			return multDarkDone;
		}			
		if(setFitsHeaders(multDarkCommand,multDarkDone) == false)
			return multDarkDone;
		if(setFilterWheelFitsHeaders(multDarkCommand,multDarkDone) == false)
			return multDarkDone;
	// do darks
		index = 0;
		reduceFilenameList = new Vector();
		while(index < multDarkCommand.getNumberExposures())
		{
			// setup per-frame FITS headers
			if(setPerFrameFitsHeaders(multDarkCommand,multDarkDone,FitsHeaderDefaults.OBSTYPE_VALUE_DARK,
						  multDarkCommand.getExposureTime(),
						  multDarkCommand.getNumberExposures(),index+1) == false)
				return multDarkDone;
			// update ISS FITS headers
			if(getFitsHeadersFromISS(multDarkCommand,multDarkDone) == false)
				return multDarkDone;
			if(testAbort(multDarkCommand,multDarkDone) == true)
				return multDarkDone;
			// do dark
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				 ":processCommand:Starting sendTakeDarkFrameCommand.");
			try
			{
				filename = sendTakeDarkFrameCommand(multDarkCommand.getExposureTime(),(index == 0));
			}
			catch(Exception e )
			{
				loci.error(this.getClass().getName()+":processCommand:sendTakeDarkFrameCommand failed:",e);
				multDarkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2701);
				multDarkDone.setErrorString(this.getClass().getName()+
							    ":processCommand:sendTakeDarkFrameCommand failed:"+e);
				multDarkDone.setSuccessful(false);
				return multDarkDone;
			}
			if(testAbort(multDarkCommand,multDarkDone) == true)
				return multDarkDone;
			// update status
			status.setExposureNumber(index+1);			
			status.setExposureFilename(filename);
		// send acknowledge to say frame is completed.
			filenameAck = new FILENAME_ACK(multDarkCommand.getId());
			filenameAck.setTimeToComplete(multDarkCommand.getExposureTime()+status.getMaxReadoutTime()+
						      serverConnectionThread.getDefaultAcknowledgeTime());
			filenameAck.setFilename(filename);
			try
			{
				serverConnectionThread.sendAcknowledge(filenameAck);
			}
			catch(IOException e)
			{
				loci.error(this.getClass().getName()+
					   ":processCommand:sendAcknowledge:"+command+":"+e.toString(),e);
				multDarkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2702);
				multDarkDone.setErrorString(e.toString());
				multDarkDone.setSuccessful(false);
				return multDarkDone;
			}
		// Send dark filename to DpRt to be reduced.
			if(reduceCalibrate(multDarkCommand,multDarkDone,filename) == false)
				return multDarkDone;
	       // send acknowledge to say frame has been reduced.
			calibrateDpAck = new CALIBRATE_DP_ACK(command.getId());
			calibrateDpAck.setTimeToComplete(multDarkCommand.getExposureTime()+status.getMaxReadoutTime()+
						      serverConnectionThread.getDefaultAcknowledgeTime());
	      // copy Data Pipeline results from DONE to ACK
			calibrateDpAck.setFilename(multDarkDone.getFilename());
			calibrateDpAck.setPeakCounts(multDarkDone.getPeakCounts());
			calibrateDpAck.setMeanCounts(multDarkDone.getMeanCounts());
			try
			{
				serverConnectionThread.sendAcknowledge(calibrateDpAck);
			}
			catch(IOException e)
			{
				loci.error(this.getClass().getName()+
					    ":processCommand:sendAcknowledge(DP):"+command+":",e);
				multDarkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2703);
				multDarkDone.setErrorString(e.toString());
				multDarkDone.setSuccessful(false);
				return multDarkDone;
			}
		// add filename to list for data pipeline processing.
			reduceFilenameList.add(filename);
		// test whether an abort has occured.
			if(testAbort(multDarkCommand,multDarkDone) == true)
				return multDarkDone;
			index++;
		}
	// return done object.
	// meanCounts and peakCounts set by reduceCalibrate for last image reduced.
		multDarkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		multDarkDone.setErrorString("");
		multDarkDone.setSuccessful(true);
		return multDarkDone;
	}
}
