// MULTRUNImplementation.java
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
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: MULTRUNImplementation.java $
 * @see ngat.loci.EXPOSEImplementation
 */
public class MULTRUNImplementation extends EXPOSEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public MULTRUNImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTRUN&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTRUN";
	}

	/**
	 * This method returns the MULTRUN command's acknowledge time. 
         * <ul>
         * <li>We construct an ACK object instance.
	 * <li>The acknowledge time is set to the MULTRUN exposure length plus the maximum readout time
	 *     (retrieved from the Loci status object) plus the server connection thread's default acknowledge time.
         * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #status
	 * @see LociStatus#getMaxReadoutTime
	 * @see #serverConnectionThread
	 * @see MULTRUN#getExposureTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(multRunCommand.getExposureTime()+status.getMaxReadoutTime()+
			serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}
	
	/**
	 * This method implements the MULTRUN command. 
	 * <ul>
	 * <li>We initialise the status objects exposure status (setExposureCount / setExposureNumber).
	 * <li>It moves the fold mirror to the correct location.
	 * <li>We determine the OBSTYPE from the standard flag.
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the 
	 *     CCD Flask API.
	 * <li>setFilterWheelFitsHeaders is called to get the current filter wheel position, 
	 *     and set some FITS headers based on this.
	 * <li>For each exposure it performs the following:
	 *	<ul>
	 *      <li>We call setPerFrameFitsHeaders to set the per-frame FITS headers.
	 *      <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). 
	 *          These are sent on to the CCD Flask API.
	 * 	<li>It performs an exposure by calling sendTakeExposureCommand.
	 * 	<li>We update the status object (setExposureNumber);
	 *      <li>We send a MULTRUN_ACK to the client updating them with the returned filename, 
	 *          and keeping the connection open.
	 * 	<li>Keeps track of the generated filenames in the list.
	 * 	</ul>
	 * <li>It sets up the return values to return to the client.
	 * </ul>
	 * The resultant last filename or the relevant error code is put into the an object of class MULTRUN_DONE and
	 * returned. During execution of these operations the abort flag is tested to see if we need to
	 * stop the implementation of this command.
	 * @see ngat.loci.LociStatus#setExposureCount
	 * @see ngat.loci.LociStatus#setExposureNumber
	 * @see ngat.loci.EXPOSEImplementation#sendTakeExposureCommand
	 * @see ngat.loci.EXPOSEImplementation#reduceExpose
	 * @see ngat.loci.CommandImplementation#testAbort
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFilterWheelFitsHeaders
	 * @see ngat.loci.HardwareImplementation#getFitsHeadersFromISS
	 * @see ngat.loci.HardwareImplementation#setPerFrameFitsHeaders
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		MULTRUN_ACK multRunAck = null;
		MULTRUN_DP_ACK multRunDpAck = null;
		MULTRUN_DONE multRunDone = new MULTRUN_DONE(command.getId());
		String obsType = null;
		String filename = null;
		String exposureType = null;
		int index;
		
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
	// setup exposure status.
		status.setExposureCount(multRunCommand.getNumberExposures());
		status.setExposureNumber(0);
	// move the fold mirror to the correct location
		if(moveFold(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		if(multRunCommand.getStandard())
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_STANDARD;
			exposureType = new String("standard");
		}
		else
		{
			obsType = FitsHeaderDefaults.OBSTYPE_VALUE_EXPOSURE;
			exposureType = new String("exposure");
		}
		// initial FITS headers setup
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			multRunDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1002);
			multRunDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			multRunDone.setSuccessful(false);
			return multRunDone;
		}			
		if(setFitsHeaders(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(setFilterWheelFitsHeaders(multRunCommand,multRunDone) == false)
			return multRunDone;
	// do exposures
		index = 0;
		while(index < multRunCommand.getNumberExposures())
		{
			// setup per-frame FITS headers
			if(setPerFrameFitsHeaders(multRunCommand,multRunDone,obsType,
						  multRunCommand.getExposureTime(),
						  multRunCommand.getNumberExposures(),index+1) == false)
				return multRunDone;
			// update ISS FITS headers
			if(getFitsHeadersFromISS(multRunCommand,multRunDone) == false)
				return multRunDone;
			if(testAbort(multRunCommand,multRunDone) == true)
				return multRunDone;
			// do exposure
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				 ":processCommand:Starting sendTakeExposureCommand.");
			try
			{
				filename = sendTakeExposureCommand(multRunCommand.getExposureTime(),
								   (index == 0),exposureType);
			}
			catch(Exception e )
			{
				loci.error(this.getClass().getName()+":processCommand:sendTakeExposureCommand failed:",
					   e);
				multRunDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1000);
				multRunDone.setErrorString(this.getClass().getName()+
							   ":processCommand:sendTakeExposureCommand failed:"+e);
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			if(testAbort(multRunCommand,multRunDone) == true)
				return multRunDone;
		// update status
			status.setExposureNumber(index+1);			
			status.setExposureFilename(filename);
		// send acknowledge to say frame is completed.
			multRunAck = new MULTRUN_ACK(command.getId());
			multRunAck.setTimeToComplete(multRunCommand.getExposureTime()+status.getMaxReadoutTime()+
						     serverConnectionThread.getDefaultAcknowledgeTime());
			multRunAck.setFilename(filename);
			try
			{
				serverConnectionThread.sendAcknowledge(multRunAck);
			}
			catch(IOException e)
			{
				loci.error(this.getClass().getName()+
					":processCommand:sendAcknowledge:"+command+":"+e.toString());
				multRunDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1001);
				multRunDone.setErrorString(e.toString());
				multRunDone.setSuccessful(false);
				return multRunDone;
			}
			// if data pipelining flag has been set, call DpRt
			if(multRunCommand.getPipelineProcess())
			{
				// do reduction.
				if(reduceExpose(multRunCommand,multRunDone,filename) == false)
					return multRunDone;
				// send acknowledge to say frame has been reduced.
				multRunDpAck = new MULTRUN_DP_ACK(command.getId());
				multRunDpAck.setTimeToComplete(multRunCommand.getExposureTime()+status.getMaxReadoutTime()+
							       serverConnectionThread.getDefaultAcknowledgeTime());
				// copy Data Pipeline results from DONE to ACK
				multRunDpAck.setFilename(multRunDone.getFilename());
				multRunDpAck.setCounts(multRunDone.getCounts());
				multRunDpAck.setSeeing(multRunDone.getSeeing());
				multRunDpAck.setXpix(multRunDone.getXpix());
				multRunDpAck.setYpix(multRunDone.getYpix());
				multRunDpAck.setPhotometricity(multRunDone.getPhotometricity());
				multRunDpAck.setSkyBrightness(multRunDone.getSkyBrightness());
				multRunDpAck.setSaturation(multRunDone.getSaturation());
				try
				{
					serverConnectionThread.sendAcknowledge(multRunDpAck);
				}
				catch(IOException e)
				{
					loci.error(this.getClass().getName()+
						   ":processCommand:sendAcknowledge(DP):"+command+":"+e.toString());
					multRunDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1203);
					multRunDone.setErrorString("sendAcknowledge(DP) failed:"+e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
			}// end if doing data pipelining
		// test whether an abort has occured.
			if(testAbort(multRunCommand,multRunDone) == true)
				return multRunDone;
			index++;
		}
	// setup return values.
	// setCounts,setFilename,setSeeing,setXpix,setYpix 
	// setPhotometricity, setSkyBrightness, setSaturation set by reduceExpose for last image reduced.
	// if data pipelining has not been done, set returned data values to blank
		if(multRunCommand.getPipelineProcess() == false)
		{
			multRunDone.setCounts(0);
			multRunDone.setFilename(filename);
			multRunDone.setSeeing(0.0f);
			multRunDone.setXpix(0.0f);
			multRunDone.setYpix(0.0f);
			multRunDone.setPhotometricity(0.0f);
			multRunDone.setSkyBrightness(0.0f);
			multRunDone.setSaturation(false);
		}
		multRunDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		multRunDone.setErrorString("");
		multRunDone.setSuccessful(true);
	// return done object.
		return multRunDone;
	}
}
