// GET_STATUSImplementation.java
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
import ngat.util.logging.*;
import ngat.util.ExecuteCommand;

/**
 * This class provides the implementation for the GET_STATUS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 * @see ngat.loci.HardwareImplementation
 */
public class GET_STATUSImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The index in the commsInstrumentStatus array of the detector comms instrument status.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_DETECTOR = 0;
	/**
	 * The index in the commsInstrumentStatus array of the filter wheel
	 * comms instrument status.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_FILTER_WHEEL = 1;
	/**
	 * The number of elements in the commsInstrumentStatus array.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_COUNT = 2;
	/**
	 * This hashtable is created in processCommand, and filled with status data,
	 * and is returned in the GET_STATUS_DONE object.
	 * Could be declared:  Generic:&lt;String, Object&gt; but this is not supported by Java 1.4.
	 */
	private Hashtable hashTable = null;
	/**
	 * Standard status string passed back in the hashTable, describing the detector temperature status health,
	 * using the standard keyword KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS. 
	 * Initialised to VALUE_STATUS_UNKNOWN.
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_UNKNOWN
	 */
	private String detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
	/**
	 * An array of standard status strings passed back in the hashTable, 
	 * describing the communication status of various bits of software/hardware the 
	 * Loci robotic control system talks to.
	 * @see #COMMS_INSTRUMENT_STATUS_COUNT
	 */
	private String commsInstrumentStatus[] = new String[COMMS_INSTRUMENT_STATUS_COUNT];
	/**
	 * The current overall mode (status) of the Loci control system.
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_EXPOSING
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_READING_OUT
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_ERROR
	 */
	protected int currentMode;
	
	/**
	 * Constructor.
	 */
	public GET_STATUSImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.GET_STATUS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.GET_STATUS";
	}

	/**
	 * This method gets the GET_STATUS command's acknowledge time. 
	 * This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the GET_STATUS command. 
	 * The local hashTable is setup (returned in the done object) and a local copy of status setup.
	 * <ul>
	 * <li>getCCDFlaskConnectionData is called to get the CCD Flask API end-point address/port number.
	 * <li>getFilterWheelFlaskConnectionData is called to get the filter wheel Flask API end-point address/port number.
	 * <li>getExposureStatus is called to get the exposure status into the exposureStatus and exposureStatusString
	 *     variables.
	 * <li>"Exposure Status" and "Exposure Status String" status properties are added to the hashtable.
	 * <li>The "Instrument" status property is set to the "loci.get_status.instrument_name" property value.
	 * <li>The detectorTemperatureInstrumentStatus is initialised.
	 * <li>The "currentCommand" status hashtable value is set to the currently executing command.
	 * <li>getFilterWheelStatus is called to add some filter wheel status to the hashtable.
	 * <li>We set "Exposure Count" to the currently executing command's expected exposure count stored in
	 *     the LociStatus instance LociStatus.getExposureCount().
	 * <li>We set "Exposure Number" to the currently executing command's current exposure index stored in
	 *     the LociStatus instance LociStatus.getExposureNumber().
	 * <li>getExposureProgress is called to add some basic status to the hashtable.
	 * <li>getIntermediateStatus is called if the GET_STATUS command level is at least intermediate.
	 * <li>getFullStatusis called if the GET_STATUS command level is at least full.
	 * </ul>
	 * An object of class GET_STATUS_DONE is returned, with the information retrieved.
	 * @param command The GET_STATUS command.
	 * @return An object of class GET_STATUS_DONE is returned.
	 * @see #loci
	 * @see #status
	 * @see #hashTable
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #commsInstrumentStatus
	 * @see #getFilterWheelStatus
	 * @see #getExposureStatus
	 * @see #getExposureProgress
	 * @see #getIntermediateStatus
	 * @see #getFullStatus
	 * @see #currentMode
	 * @see HardwareImplementation#getCCDFlaskConnectionData
	 * @see LociStatus#getProperty
	 * @see LociStatus#getExposureCount
	 * @see LociStatus#getExposureNumber
	 * @see GET_STATUS#getLevel
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		GET_STATUS getStatusCommand = (GET_STATUS)command;
		GET_STATUS_DONE getStatusDone = new GET_STATUS_DONE(command.getId());
		ISS_TO_INST currentCommand = null;

		try
		{
			// Create new hashtable to be returned
			// v1.5 generic typing of collections:<String, Object>, can't be used due to v1.4 compatibility
			hashTable = new Hashtable();
			// get CCD Flask API comms configuration
			getCCDFlaskConnectionData();
			// get filter wheel Flask API comms configuration
			getFilterWheelFlaskConnectionData();
			// exposure status
			// Also sets currentMode
			getExposureStatus(); 
			getStatusDone.setCurrentMode(currentMode); 
			// What instrument is this?
			hashTable.put("Instrument",status.getProperty("loci.get_status.instrument_name"));
			// Initialise Standard status to UNKNOWN
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
				      detectorTemperatureInstrumentStatus);
			hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,GET_STATUS_DONE.VALUE_STATUS_UNKNOWN);
			// initialise comms status to unknown
			for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
			{
				commsInstrumentStatus[i] = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			}
			// current command
			currentCommand = status.getCurrentCommand();
			if(currentCommand == null)
				hashTable.put("currentCommand","");
			else
				hashTable.put("currentCommand",currentCommand.getClass().getName());
			// basic information
			getFilterWheelStatus();
			// "Exposure Count" is searched for by the IcsGUI
			hashTable.put("Exposure Count",new Integer(status.getExposureCount()));
			// "Exposure Number" is searched for by the IcsGUI
			hashTable.put("Exposure Number",new Integer(status.getExposureNumber()));
			// Exposure Progress
			// The exposure progress command will only succeed if the camera is in DRV_ACQUIRING,
			// so we need to have run getExposureStatus() before getExposureProgress() so currentMode is set correctly.
			// "Exposure Length" is needed for IcsGUI
			// "Elapsed Exposure Time" is needed for IcsGUI.
			getExposureProgress();
			//getStatusExposureLength();  
			// "Exposure Start Time" is needed for IcsGUI
			//getStatusExposureStartTime(); 
			//getStatusExposureMultrun(); 
			//getStatusExposureRun();
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+
				       ":processCommand:Retrieving basic status failed.",e);
			getStatusDone.setDisplayInfo(hashTable);
			getStatusDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2500);
			getStatusDone.setErrorString("processCommand:Retrieving basic status failed:"+e);
			getStatusDone.setSuccessful(false);
			return getStatusDone;
		}
	// intermediate level information - basic plus controller calls.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_INTERMEDIATE)
		{
			getIntermediateStatus();
		}// end if intermediate level status
	// Get full status information.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_FULL)
		{
			getFullStatus();
		}
	// set hashtable and return values.
		getStatusDone.setDisplayInfo(hashTable);
		getStatusDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		getStatusDone.setErrorString("");
		getStatusDone.setSuccessful(true);
	// return done object.
		return getStatusDone;
	}

	/**
	 * Get the status of the filter wheel.
	 * <ul>
	 * <li>We construct an instance of GetFilterPositionCommand, and run it, to get filter position data from the filter wheel Flask API.
	 * <li>If the command run fails, we update commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] to FAIL, and log the error, and return.
	 * <li>If the command run succeeds, we set the "Filter Wheel:1" hashTable entry to the filter name returned, 
	 *     and set the "Filter Wheel Position:1" hashtable entry to the filter wheel position returned.
	 * <li>We construct an instance of GetStatusCommand, and run it, to get filter wheel connection data from the filter wheel Flask API.
	 * <li>If the command run fails, we update commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] to FAIL, and log the error, and return.
	 * <li>If the command run succeeds, we set the "Filter Wheel Connection Status:1" hashTable entry to the filter wheel connection status.
	 * <li>We update the commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] to OK, to say we can communicate with the filter wheel.
	 * </ul>
	 * @exception UnknownHostException Thrown if setting the hostname address of the 
	 *            GetFilterPositionCommand or GetStatusCommand fails.
	 * @see #filterWheelFlaskHostname
	 * @see #filterWheelFlaskPortNumber
	 * @see #hashTable
	 * @see #COMMS_INSTRUMENT_STATUS_FILTER_WHEEL
	 * @see #commsInstrumentStatus
	 * @see ngat.loci.filterwheel.GetFilterPositionCommand
	 * @see ngat.loci.filterwheel.GetFilterPositionCommand#getFilterName
	 * @see ngat.loci.filterwheel.GetFilterPositionCommand#getFilterPosition
	 * @see ngat.loci.filterwheel.GetStatusCommand
	 * @see ngat.loci.filterwheel.GetStatusCommand#getConnectionStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void getFilterWheelStatus() throws UnknownHostException
	{
		GetFilterPositionCommand filterPositionCommand = null;
		GetStatusCommand getStatusCommand = null;
		Exception returnException = null;
		String filterName = null;
		String filterWheelConnectionStatus = null;
		int returnCode,filterWheelPosition;

		// Setup GetFilterPositionCommand
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:started for filter wheel Flask API :Hostname: "+
			 filterWheelFlaskHostname+" Port Number: "+filterWheelFlaskPortNumber+".");
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:Send GetFilterPositionCommand command to Flask API.");
		filterPositionCommand = new GetFilterPositionCommand();
		filterPositionCommand.setAddress(filterWheelFlaskHostname);
		filterPositionCommand.setPortNumber(filterWheelFlaskPortNumber);
		// actually send the command to the filter wheel Flask API
		filterPositionCommand.run();
		// check the parsed reply
		if(filterPositionCommand.isReturnStatusSuccess() == false)
		{
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] = GET_STATUS_DONE.
				VALUE_STATUS_FAIL;
			hashTable.put("Filter Wheel.Comms.Status",
				      commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL]);
			returnCode = filterPositionCommand.getHttpResponseCode();
			returnException = filterPositionCommand.getRunException();
			loci.log(Logging.VERBOSITY_TERSE,
				 "getFilterWheelStatus:get filter position command failed with return code "+
				 returnCode+" run exception:"+returnException);
			loci.error("getFilterWheelStatus:get filter position command failed with return code "+
				   returnCode+" run exception:"+returnException,returnException);
			return;
		}
		// retrieve returned data and put it in the hashtable
		filterName = filterPositionCommand.getFilterName();
		hashTable.put("Filter Wheel:1",new String(filterName));
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:filter wheel filter name:"+filterName);
		filterWheelPosition = filterPositionCommand.getFilterPosition();
		hashTable.put("Filter Wheel Position:1",new Integer(filterWheelPosition));
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:filter wheel position:"+filterWheelPosition);
		// Setup GetStatusCommand
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:Send GetStatusCommand command to Flask API.");
		getStatusCommand = new GetStatusCommand();
		getStatusCommand.setAddress(filterWheelFlaskHostname);
		getStatusCommand.setPortNumber(filterWheelFlaskPortNumber);
		// actually send the command to the filter wheel Flask API
		getStatusCommand.run();
		// check the parsed reply
		if(getStatusCommand.isReturnStatusSuccess() == false)
		{
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] = GET_STATUS_DONE.
				VALUE_STATUS_FAIL;
			hashTable.put("Filter Wheel.Comms.Status",
				      commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL]);
			returnCode = getStatusCommand.getHttpResponseCode();
			returnException = getStatusCommand.getRunException();
			loci.log(Logging.VERBOSITY_TERSE,
				 "getFilterWheelStatus:get filter wheel status command failed with return code "+
				 returnCode+" run exception:"+returnException);
			loci.error("getFilterWheelStatus:get filter position command failed with return code "+
				   returnCode+" run exception:"+returnException,returnException);
			return;
		}
		// retrieve returned data and put it in the hashtable
		filterWheelConnectionStatus = getStatusCommand.getConnectionStatus();
		hashTable.put("Filter Wheel Connection Status:1",new String(filterWheelConnectionStatus));
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:filter wheel connection status:"+
			 filterWheelConnectionStatus);
		// update filter wheel comms status to "OK"
		commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL] = GET_STATUS_DONE.VALUE_STATUS_OK;
		hashTable.put("Filter Wheel.Comms.Status",
			      commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_FILTER_WHEEL]);
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getFilterWheelStatus:finished.");
		
	}
	
	/**
	 * Get the exposure status. 
	 * This retrieved using an instance of GetCameraStatusCommand.
	 * The "Camera Status" keyword/value pairs are generated from the returned status. 
	 * The currentMode is set as either MODE_IDLE, or MODE_EXPOSING if the CCD Flask API getCameraStatus returns
	 * "DRV_ACQUIRING".
	 * @exception Exception Thrown if an error occurs.
	 * @see #currentMode
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.GetCameraStatusCommand
	 * @see ngat.loci.ccd.GetCameraStatusCommand#getCameraStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_EXPOSING
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_ERROR
	 */
	protected void getExposureStatus() throws Exception
	{
		GetCameraStatusCommand statusCommand = null;
		Exception returnException = null;
		int returnCode;
		String cameraStatus;
		
		// initialise currentMode to IDLE
		currentMode = GET_STATUS_DONE.MODE_IDLE;
		// Setup GetCameraStatusCommand
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureStatus:started for CCD Flask API :Hostname: "+
			 ccdFlaskHostname+" Port Number: "+ccdFlaskPortNumber+".");
		statusCommand = new GetCameraStatusCommand();
		statusCommand.setAddress(ccdFlaskHostname);
		statusCommand.setPortNumber(ccdFlaskPortNumber);
		// actually send the command to the CCD Flask API
		statusCommand.run();
		// check the parsed reply
		if(statusCommand.isReturnStatusSuccess() == false)
		{
			returnCode = statusCommand.getHttpResponseCode();
			returnException = statusCommand.getRunException();
			loci.log(Logging.VERBOSITY_TERSE,
				 "getExposureStatus:exposure status command failed with return code "+
				 returnCode+" run exception:"+returnException);
			throw new Exception(this.getClass().getName()+
					    ":getExposureStatus:exposure status command failed with return code "+returnCode,returnException);
		}
		cameraStatus = statusCommand.getCameraStatus();
		hashTable.put("Camera Status",new String(cameraStatus));
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureStatus:finished with camera status:"+
			  cameraStatus);
		// change currentMode dependant on what the CCD camera is doing
		if(cameraStatus.equals("DRV_IDLE"))
			currentMode = GET_STATUS_DONE.MODE_IDLE;
		else if(cameraStatus.equals("DRV_ACQUIRING"))
			currentMode = GET_STATUS_DONE.MODE_EXPOSING;
	}

	/**
	 * Get the exposure progress. 
	 * This retrieved using an instance of GetExposureProgressCommand.
	 * The "Exposure Length" keyword/value pair is generated from the returned exposure time. 
	 * The "Elapsed Exposure Time" keyword/value pair is generated from the returned elapsed time. 
	 * The "Remaining Exposure Time" keyword/value pair is generated from the returned remaining time. 
	 * @exception Exception Thrown if an error occurs.
	 * @see LociConstants#MILLISECONDS_PER_SECOND
	 * @see #currentMode
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.GetExposureProgressCommand
	 * @see ngat.loci.ccd.GetExposureProgressCommand#getExposureTime
	 * @see ngat.loci.ccd.GetExposureProgressCommand#getElapsedTime
	 * @see ngat.loci.ccd.GetExposureProgressCommand#getRemainingTime
	 */
	protected void getExposureProgress() throws Exception
	{
		GetExposureProgressCommand statusCommand = null;
		Exception returnException = null;
		int returnCode;
		double elapsedExposureLengthS,exposureLengthS,remainingExposureLengthS;
		int elapsedExposureLengthMs,exposureLengthMs,remainingExposureLengthMs;
		
		// Setup GetExposureProgressCommand
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureProgress:started for CCD Flask API :Hostname: "+
			 ccdFlaskHostname+" Port Number: "+ccdFlaskPortNumber+".");
		// set returned values to some sensible defaults
		exposureLengthS = 0.0;
		elapsedExposureLengthS = 0.0;
		remainingExposureLengthS = 0.0;
		// GetExposureProgressCommand only returns success when we are in DRV_ACQUIRING
		if(currentMode == GET_STATUS_DONE.MODE_EXPOSING)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureProgress:Current Mode is DRV_ACQUIRING, "+
				 "so we can call GetExposureProgressCommand to retrieve current exposure progress.");
			statusCommand = new GetExposureProgressCommand();
			statusCommand.setAddress(ccdFlaskHostname);
			statusCommand.setPortNumber(ccdFlaskPortNumber);
			// actually send the command to the CCD Flask API
			statusCommand.run();
			// check the parsed reply
			if(statusCommand.isReturnStatusSuccess())
			{
				exposureLengthS = statusCommand.getExposureTime();
				elapsedExposureLengthS = statusCommand.getElapsedTime();
				remainingExposureLengthS = statusCommand.getRemainingTime();
			}
			else // a failure has occured
			{
				// This command can fail if the camera is not currently DRV_ACQURIING, so
				// just log the error on failure.
				returnCode = statusCommand.getHttpResponseCode();
				returnException = statusCommand.getRunException();
				loci.log(Logging.VERBOSITY_TERSE,
					 "getExposureProgress:command failed with return code "+returnCode+" run exception:"+returnException);
				//throw new Exception(this.getClass().getName()+
				//		    ":getExposureProgress:command failed with return code "+
				//		    returnCode,returnException);
			}
		}
		else
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureProgress:Current Mode is NOT DRV_ACQUIRING, "+
				 "Setting current exposure progress to all zeros.");
		}
		// "Exposure Length"
		// GetExposureProgressCommand returns this in decimal seconds, we want it in integer milliseconds
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Exposure Length is:"+
				 exposureLengthS+" seconds.");
		exposureLengthMs = (int)(exposureLengthS*((double)LociConstants.MILLISECONDS_PER_SECOND));
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Exposure Length is:"+
			 exposureLengthMs+" milliseconds.");
		hashTable.put("Exposure Length",new Integer(exposureLengthMs));
		// "Elapsed Exposure Time"
		// GetExposureProgressCommand returns this in decimal seconds, we want it in integer milliseconds
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Elapsed Exposure Length is:"+
			 elapsedExposureLengthS+" seconds.");
		elapsedExposureLengthMs = (int)(elapsedExposureLengthS*((double)LociConstants.MILLISECONDS_PER_SECOND));
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Elapsed Exposure Length is:"+
			 elapsedExposureLengthMs+" milliseconds.");
		hashTable.put("Elapsed Exposure Time",new Integer(elapsedExposureLengthMs));
		// "Remaining Exposure Time"
		// This is not a field that the IcsGUI looks for, but we have the information, so lets add it
		// GetExposureProgressCommand returns this in decimal seconds, we want it in integer milliseconds
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Remaining Exposure Time is:"+
			 remainingExposureLengthS+" seconds.");
		remainingExposureLengthMs = (int)(remainingExposureLengthS*
						  ((double)LociConstants.MILLISECONDS_PER_SECOND));
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,"getExposureProgress:Remaining Exposure Time is:"+
			 remainingExposureLengthMs+" milliseconds.");
		hashTable.put("Remaining Exposure Time",new Integer(remainingExposureLengthMs));
	}

	/**
	 * Get intermediate level status. This is:
	 * <ul>
	 * <li>detector temperature information from the camera.
	 * </ul>
	 * The overall health and well-being statii are then computed using setInstrumentStatus.
	 * @see #hashTable
	 * @see #commsInstrumentStatus
	 * @see #getTemperature
	 * @see #setInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	private void getIntermediateStatus()
	{
		try
		{
			getTemperature();
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_DETECTOR] = GET_STATUS_DONE.
				VALUE_STATUS_OK;
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+
				     ":getIntermediateStatus:Retrieving temperature status failed.",e);
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_DETECTOR] = GET_STATUS_DONE.
				VALUE_STATUS_FAIL;
		}
		hashTable.put("Detector.Comms.Status",commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_DETECTOR]);
	// Standard status
		setInstrumentStatus();
	}

	/**
	 * Get the current detector temperature.
	 * An instance of GetTemperatureCommand is used to send the command to the CCD Flask API end-point.
	 * The returned value is stored in
	 * the hashTable, under the "Temperature" key (converted to Kelvin). 
	 * A timestamp is also retrieved (when the temperature was actually measured, it may be a cached value), 
	 * and this is stored in the "Temperature Timestamp" key.
	 * setDetectorTemperatureInstrumentStatus is called with the detector temperature to set
	 * the detector temperature health and wellbeing values.
	 * @exception Exception Thrown if an error occurs.
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see #hashTable
	 * @see #setDetectorTemperatureInstrumentStatus
	 * @see ngat.loci.Loci#CENTIGRADE_TO_KELVIN
	 * @see ngat.loci.ccd.GetTemperatureCommand
	 * @see ngat.loci.ccd.GetTemperatureCommand#getTemperature
	 * @see ngat.loci.ccd.GetTemperatureCommand#getCoolingEnabled
	 * @see ngat.loci.ccd.GetTemperatureCommand#getCoolingStatus
	 */
	protected void getTemperature() throws Exception
	{
		GetTemperatureCommand statusCommand = null;
		String coolingStatus = null;
		Exception returnException = null;
		int returnCode;
		double temperature;
		Date timestamp;
		boolean coolingEnabled;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getTemperature:started for CCD Flask API ("+
			   ccdFlaskHostname+":"+ccdFlaskPortNumber+") end-point.");
		statusCommand = new GetTemperatureCommand();
		statusCommand.setAddress(ccdFlaskHostname);
		statusCommand.setPortNumber(ccdFlaskPortNumber);
		// actually send the command to the CCD Flask API
		statusCommand.run();
		// check the parsed reply
		if(statusCommand.isReturnStatusSuccess() == false)
		{
			returnCode = statusCommand.getHttpResponseCode();
			returnException = statusCommand.getRunException();
			loci.log(Logging.VERBOSITY_TERSE,
				 "getTemperature:get temperature command failed with return code "+returnCode+" run exception:"+returnException);
			throw new Exception(this.getClass().getName()+
					    ":getTemperature:get temperature command failed with return code "+returnCode,returnException);
		}
		temperature = statusCommand.getTemperature();
		coolingEnabled = statusCommand.getCoolingEnabled();
		coolingStatus = statusCommand.getCoolingStatus();
		hashTable.put("Temperature",new Double(temperature+Loci.CENTIGRADE_TO_KELVIN));
		hashTable.put("Cooling Enabled",new Boolean(coolingEnabled));
		hashTable.put("Cooling Status",new String(coolingStatus));
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getTemperature:finished for CCD Flask API ("+
			   ccdFlaskHostname+":"+ccdFlaskPortNumber+") with temperature:"+temperature+
			   " with cooling status "+coolingStatus+" and cooling enabled:"+coolingEnabled);
		setDetectorTemperatureInstrumentStatus(temperature);
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"getTemperature:finished.");
	}

	/**
	 * Set the standard entry for detector temperature in the hashtable based upon the current temperature.
	 * Reads the folowing config:
	 * <ul>
	 * <li>loci.get_status.detector.temperature.warm.warn
	 * <li>loci.get_status.detector.temperature.warm.fail
	 * <li>loci.get_status.detector.temperature.cold.warn
	 * <li>loci.get_status.detector.temperature.cold.fail
	 * </ul>
	 * @param temperature The current detector temperature in degrees C.
	 * @exception NumberFormatException Thrown if the config is not a valid double.
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void setDetectorTemperatureInstrumentStatus(double temperature) throws NumberFormatException
	{
		double warmWarnTemperature,warmFailTemperature,coldWarnTemperature,coldFailTemperature;

		// get config for warn and fail temperatures
		warmWarnTemperature = status.getPropertyDouble("loci.get_status.detector.temperature.warm.warn");
		warmFailTemperature = status.getPropertyDouble("loci.get_status.detector.temperature.warm.fail");
		coldWarnTemperature = status.getPropertyDouble("loci.get_status.detector.temperature.cold.warn");
		coldFailTemperature = status.getPropertyDouble("loci.get_status.detector.temperature.cold.fail");
		// initialise status to OK
		detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		if(temperature > warmFailTemperature)
		{
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		}
		else if(temperature > warmWarnTemperature)
		{
			// only set to WARN if we are currently OKAY (i.e. if we are FAIL stay FAIL) 
			if(detectorTemperatureInstrumentStatus == GET_STATUS_DONE.VALUE_STATUS_OK)
			{
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
			}
		}
		else if(temperature < coldFailTemperature)
		{
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		}
		else if(temperature < coldWarnTemperature)
		{
			if(detectorTemperatureInstrumentStatus == GET_STATUS_DONE.VALUE_STATUS_OK)
			{
				// only set to WARN if we are currently OKAY (i.e. if we are FAIL stay FAIL) 
				detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
			}
		}
		// set hashtable entry
		hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
			      detectorTemperatureInstrumentStatus);
	}

	/**
	 * Set the overall instrument status keyword in the hashtable. This is derived from sub-system keyword values,
	 * currently only the detector temperature. HashTable entry KEYWORD_INSTRUMENT_STATUS)
	 * should be set to the worst of OK/WARN/FAIL. If sub-systems are UNKNOWN, OK is returned.
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #COMMS_INSTRUMENT_STATUS_COUNT
	 * @see #commsInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void setInstrumentStatus()
	{
		String instrumentStatus;

		// default to OK
		instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		// if a sub-status is in warning, overall status is in warning
		if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
		{
			if(commsInstrumentStatus[i].equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
				instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		}
		// if a sub-status is in fail, overall status is in fail. This overrides a previous warn
	        if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
		{
			if(commsInstrumentStatus[i].equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
				instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		}
		// set standard status in hashtable
		hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,instrumentStatus);
	}

	/**
	 * Method to get misc status, when level FULL has been selected.
	 * The following data is put into the hashTable:
	 * <ul>
	 * <li><b>Log Level</b> The current logging level Loci is using.
	 * <li><b>Disk Usage</b> The results of running a &quot;df -k&quot;, to get the disk usage.
	 * <li><b>Process List</b> The results of running a &quot;ps -e -o pid,pcpu,vsz,ruser,stime,time,args&quot;, 
	 * 	to get the processes running on this machine.
	 * <li><b>Uptime</b> The results of running a &quot;uptime&quot;, 
	 * 	to get system load and time since last reboot.
	 * <li><b>Total Memory, Free Memory</b> The total and free memory in the Java virtual machine.
	 * <li><b>java.version, java.vendor, java.home, java.vm.version, java.vm.vendor, java.class.path</b> 
	 * 	Java virtual machine version, classpath and type.
	 * <li><b>os.name, os.arch, os.version</b> The operating system type/version.
	 * <li><b>user.name, user.home, user.dir</b> Data about the user the process is running as.
	 * <li><b>thread.list</b> A list of threads the Loci process is running.
	 * </ul>
	 * @see #serverConnectionThread
	 * @see #hashTable
	 * @see ExecuteCommand#run
	 * @see LociStatus#getLogLevel
	 */
	private void getFullStatus()
	{
		ExecuteCommand executeCommand = null;
		Runtime runtime = null;
		StringBuffer sb = null;
		Thread threadList[] = null;
		int threadCount;

		// log level
		hashTable.put("Log Level",new Integer(status.getLogLevel()));
		// execute 'df -k' on instrument computer
		executeCommand = new ExecuteCommand("df -k");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Disk Usage",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Disk Usage",new String(executeCommand.getException().toString()));
		// execute "ps -e -o pid,pcpu,vsz,ruser,stime,time,args" on instrument computer
		executeCommand = new ExecuteCommand("ps -e -o pid,pcpu,vsz,ruser,stime,time,args");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Process List",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Process List",new String(executeCommand.getException().toString()));
		// execute "uptime" on instrument computer
		executeCommand = new ExecuteCommand("uptime");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Uptime",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Uptime",new String(executeCommand.getException().toString()));
		// get vm memory situation
		runtime = Runtime.getRuntime();
		hashTable.put("Free Memory",new Long(runtime.freeMemory()));
		hashTable.put("Total Memory",new Long(runtime.totalMemory()));
		// get some java vm information
		hashTable.put("java.version",new String(System.getProperty("java.version")));
		hashTable.put("java.vendor",new String(System.getProperty("java.vendor")));
		hashTable.put("java.home",new String(System.getProperty("java.home")));
		hashTable.put("java.vm.version",new String(System.getProperty("java.vm.version")));
		hashTable.put("java.vm.vendor",new String(System.getProperty("java.vm.vendor")));
		hashTable.put("java.class.path",new String(System.getProperty("java.class.path")));
		hashTable.put("os.name",new String(System.getProperty("os.name")));
		hashTable.put("os.arch",new String(System.getProperty("os.arch")));
		hashTable.put("os.version",new String(System.getProperty("os.version")));
		hashTable.put("user.name",new String(System.getProperty("user.name")));
		hashTable.put("user.home",new String(System.getProperty("user.home")));
		hashTable.put("user.dir",new String(System.getProperty("user.dir")));
	}
}
