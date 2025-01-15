// CONFIGImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.loci.ccd.*;
import ngat.loci.filterwheel.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.LociConfig;
import ngat.phase2.LociDetector;
import ngat.phase2.Detector;
import ngat.phase2.Window;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the CONFIG command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: CONFIGImplementation.java $
 * @see ngat.loci.HardwareImplementation
 */
public class CONFIGImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public CONFIGImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.CONFIG&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.CONFIG";
	}

	/**
	 * This method gets the CONFIG command's acknowledge time.
	 * This method returns an ACK with timeToComplete set to the &quot;loci.config.acknowledge_time &quot;
	 * held in the Loci configuration file. 
	 * If this cannot be found/is not a valid number the default acknowledge time is used instead.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set to a time (in milliseconds).
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;
		int timeToComplete = 0;

		acknowledge = new ACK(command.getId());
		try
		{
			timeToComplete += loci.getStatus().getPropertyInteger("loci.config.acknowledge_time");
		}
		catch(NumberFormatException e)
		{
			loci.error(this.getClass().getName()+":calculateAcknowledgeTime:"+e);
			timeToComplete += serverConnectionThread.getDefaultAcknowledgeTime();
		}
		acknowledge.setTimeToComplete(timeToComplete);
		return acknowledge;
	}

	/**
	 * This method implements the CONFIG command. 
	 * <ul>
	 * <li>The command is casted.
	 * <li>The DONE message is created.
	 * <li>We test for command abort.
	 * <li>We call sendSetFilterPositionByNameCommand to set the filter wheel to the position specified by the filter name.
	 * <li>We call sendSetImageWindowCommand to set the detector binning and sub-window.
	 * <li>We calculate the focus offset from "loci.focus.offset", and call setFocusOffset to tell the RCS/TCS
	 *     the focus offset required.
	 * <li>We increment the config Id.
	 * <li>We save the config name in the Loci status instance for future reference.
	 * <li>We save the coadd exposure length in the Loci status instance for future reference.
	 * <li>We return success.
	 * </ul>
	 * @see #sendSetFilterPositionByNameCommand
	 * @see #sendSetImageWindowCommand
	 * @see #testAbort
	 * @see #setFocusOffset
	 * @see #loci
	 * @see #status
	 * @see ngat.loci.Loci#getStatus
	 * @see ngat.loci.LociStatus#incConfigId
	 * @see ngat.loci.LociStatus#setConfigName
	 * @see ngat.phase2.LociConfig
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		CONFIG configCommand = null;
		LociConfig config = null;
		CONFIG_DONE configDone = null;
		String configName = null;
		String filterIdName = null;
		float focusOffset,filterFocusOffset;

		loci.log(Logging.VERBOSITY_VERY_TERSE,"CONFIGImplementation:processCommand:Started.");
	// test contents of command.
		configCommand = (CONFIG)command;
		configDone = new CONFIG_DONE(command.getId());
		if(testAbort(configCommand,configDone) == true)
			return configDone;
		if(configCommand.getConfig() == null)
		{
			loci.error(this.getClass().getName()+":processCommand:"+command+":Config was null.");
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+800);
			configDone.setErrorString(":Config was null.");
			configDone.setSuccessful(false);
			return configDone;
		}
		if((configCommand.getConfig() instanceof LociConfig) == false)
		{
			loci.error(this.getClass().getName()+":processCommand:"+
				command+":Config has wrong class:"+
				configCommand.getConfig().getClass().getName());
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+801);
			configDone.setErrorString(":Config has wrong class:"+
				configCommand.getConfig().getClass().getName());
			configDone.setSuccessful(false);
			return configDone;
		}
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
	// get config from configCommand.
		config = (LociConfig)configCommand.getConfig();
	// get configuration Id - used later
		configName = config.getId();
		loci.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
			 configCommand.getClass().getName()+
			 "\n\t:id = "+configName+
			 "\n\t:Filter = "+config.getFilterName()+
			 "\n\t:X Binning = "+config.getDetector(0).getXBin()+
			 "\n\t:Y Binning = "+config.getDetector(0).getYBin()+".");
		// If the window is active, print window data out
		if(config.getDetector(0).isActiveWindow(0))
		{
			Window window = config.getDetector(0).getWindow(0);
			
			loci.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
				 configCommand.getClass().getName()+
				 "\n\t:subwindow = {xs="+window.getXs()+",ys="+window.getYs()+
				 ",xe="+window.getXe()+",ye="+window.getYe()+"}");
		}
		// send config commands to Flask API layers
		try
		{
			sendSetFilterPositionByNameCommand(config.getFilterName());
			sendSetImageWindowCommand(config.getDetector(0));
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+command,e);
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+804);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}		
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
	// Get overall instrument focus offset
		try
		{
			focusOffset = status.getPropertyFloat("loci.focus.offset");
			loci.log(Logging.VERBOSITY_TERSE,"Command:"+
				   configCommand.getClass().getName()+":instrument focus offset = "+focusOffset+".");
		}
		catch(NumberFormatException e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+command,e);
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+806);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Get filter focus offset
		try
		{
			filterIdName = status.getFilterIdName(config.getFilterName());
			filterFocusOffset = (float)(status.getFilterIdOpticalThickness(filterIdName));
			loci.log(Logging.VERBOSITY_TERSE,"Command:"+
				   configCommand.getClass().getName()+":filter focus offset = "+filterFocusOffset+".");
			focusOffset += filterFocusOffset;
			loci.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
				   configCommand.getClass().getName()+":overall focus offset = "+focusOffset+".");
		}
		catch(NumberFormatException e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+command,e);
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+802);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// actually issue ISS OFFSET_FOCUS commmand to telescope/ISS. 
		if(setFocusOffset(configCommand.getId(),focusOffset,configDone) == false)
			return configDone;
	// Increment unique config ID.
	// This is queried when saving FITS headers to get the CONFIGID value.
		try
		{
			status.incConfigId();
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+
				command+":Incrementing configuration ID:"+e.toString());
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+809);
			configDone.setErrorString("Incrementing configuration ID:"+e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Store name of configuration used in status object
	// This is queried when saving FITS headers to get the CONFNAME value.
		status.setConfigName(configName);
	// setup return object.
		configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		configDone.setErrorString("");
		configDone.setSuccessful(true);
		loci.log(Logging.VERBOSITY_VERY_TERSE,"CONFIGImplementation:processCommand:Finished.");
	// return done object.
		return configDone;
	}

	/**
	 * Send a setFilterPositionByName filter wheel Flask API call to configure the filter wheel.
	 * <ul>
	 * <li>We get the filter wheel Flask API connection data by calling  getFilterWheelFlaskConnectionData.
	 * <li>We construct and initialise a SetFilterPositionByNameCommand instance.
	 * <li>We run the SetFilterPositionByNameCommand instance.
	 * <li>We check whether the command threw an exception, or returned an error.
	 * </ul>
	 * @param filterName The name of the filter to move the filter wheel position to.
	 * @see #getFilterWheelFlaskConnectionData
	 * @see #filterWheelFlaskHostname
	 * @see #filterWheelFlaskPortNumber
	 * @see ngat.loci.filterwheel.SetFilterPositionByNameCommand
	 * @exception UnknownHostException Thrown if the address passed to SetFilterPositionByNameCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the SetFilterPositionByNameCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected void sendSetFilterPositionByNameCommand(String filterName)  throws UnknownHostException, Exception
	{
		SetFilterPositionByNameCommand command = null;

		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendSetFilterPositionByNameCommand:started:moving to filter:"+filterName);
		// get filter wheel Flask API connection data
		getFilterWheelFlaskConnectionData();
		// setup command
		command = new SetFilterPositionByNameCommand();
		command.setAddress(filterWheelFlaskHostname);
		command.setPortNumber(filterWheelFlaskPortNumber);
		// filter name
		command.setFilterName(filterName);
		// run command
		command.run();
		// check reply
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendSetFilterPositionByNameCommand:Failed:"+command.getRunException(),
					    command.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendSetFilterPositionByNameCommand:Set Filter Position By Name Command Finished with status: "+
			 command.getReturnStatus()+" and message:"+command.getMessage()+".");
		if(command.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					":sendSetFilterPositionByNameCommand:Set Filter Position By Name Command failed with status: "+
					    command.getReturnStatus()+" and message:"+command.getMessage()+".");
		}
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendSetFilterPositionByNameCommand:finished:moved to filter:"+filterName);
	}

	/**
	 * Send a setImageWindow CCD Flask API call to configure the detector binning and sub-window.
	 * <ul>
	 * <li>We get the CCD Flask API connection data by calling  getCCDFlaskConnectionData.
	 * <li>We construct and initialise a SetImageWindowCommand instance.
	 * <li>We set the detector binning factors from the LociDetector detector parameter.
	 * <li>If the detector window is active, We set the detector sub-image from the 
	 *     LociDetector detector window parameter.
	 * <li>We run the SetImageWindowCommand instance.
	 * <li>We check whether the command threw an exception, or returned an error.
	 * </ul>
	 * @param detector An instance of LociDetector containing the binning and windowing to configure.
	 * @see #getCCDFlaskConnectionData
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.SetImageWindowCommand
	 * @see ngat.phase2.LociDetector
	 * @exception UnknownHostException Thrown if the address passed to SetImageWindowCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the SetImageWindowCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected void sendSetImageWindowCommand(Detector detector) throws UnknownHostException, Exception
	{
		SetImageWindowCommand command = null;
		Window window = null;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendSetImageWindowCommand:started.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// setup command
		command = new SetImageWindowCommand();
		command.setAddress(ccdFlaskHostname);
		command.setPortNumber(ccdFlaskPortNumber);
		// binning
		command.setHorizontalBinning(detector.getXBin());
		command.setVerticalBinning(detector.getYBin());
		// set window if required
		if(detector.isActiveWindow(0))
		{
			window = detector.getWindow(0);
			command.setHorizontalStart(window.getXs());
			command.setVerticalStart(window.getYs());
			command.setHorizontalEnd(window.getXe());
			command.setVerticalEnd(window.getYe());
		}
		// run command
		command.run();
		// check reply
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendSetImageWindowCommand:Failed:"+command.getRunException(),
					    command.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendSetImageWindowCommand:Set Image Window Command Finished with status: "+
			 command.getReturnStatus()+" and message:"+command.getMessage()+".");
		if(command.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendSetImageWindowCommand:Set Image Window Command failed with status: "+
					    command.getReturnStatus()+" and message:"+command.getMessage()+".");
		}
	}

	/**
	 * Routine to set the telescope focus offset, due to the filters selected. Sends a OFFSET_FOCUS command to
	 * the ISS. The OFFSET_FOCUS sent is the offset of Loci's focus from the nominal telescope focus.
	 * @param id The Id is used as the OFFSET_FOCUS command's id.
	 * @param focusOffset The focus offset needed.
	 * @param configDone The instance of CONFIG_DONE. This is filled in with an error message if the
	 * 	OFFSET_FOCUS fails.
	 * @return The method returns true if the telescope attained the focus offset, otherwise false is
	 * 	returned an telFocusDone is filled in with an error message.
	 */
	private boolean setFocusOffset(String id,float focusOffset,CONFIG_DONE configDone)
	{
		OFFSET_FOCUS focusOffsetCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		String filterIdName = null;
		String filterTypeString = null;

		focusOffsetCommand = new OFFSET_FOCUS(id);
	// set the commands focus offset
		focusOffsetCommand.setFocusOffset(focusOffset);
		instToISSDone = loci.sendISSCommand(focusOffsetCommand,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			loci.error(this.getClass().getName()+":focusOffset failed:"+focusOffset+":"+
				   instToISSDone.getErrorString());
			configDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+805);
			configDone.setErrorString(instToISSDone.getErrorString());
			configDone.setSuccessful(false);
			return false;
		}
		return true;
	}	
}
