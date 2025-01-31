# A stripped version of loci-ctrl src main.py
from flask import Flask, request, jsonify
import traceback
import sys
import time

app = Flask(__name__)

abort = False
exposure_time = 0
remaining_exposure_length = 0

@app.route('/takeBiasFrame', methods=['POST'])
def take_bias_frame():
    global exposure_time
    global remaining_exposure_length
    try:
        print ("takeBiasFrame invoked.")
        exposure_time = 0
        remaining_exposure_length = 0
        filename='/data/bias.fits'
        return jsonify({"status": "Success", "message": "successfully saved bias frame.", "filename": filename })
    except Exception as e:
        return handle_exception(e)

@app.route('/takeDarkFrame', methods=['POST'])
def take_dark_frame():
    global abort
    global exposure_time
    global remaining_exposure_length
    try:
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400 
        payload = request.get_json()
        if 'exposure_time' not in payload:
            return jsonify({"status": "Error", "message": "'exposure_time' is required"}), 400
        exposure_time = payload['exposure_time']
        print ("takeDarkFrame invoked with exposure length", exposure_time, " seconds.")
        abort = False
        remaining_exposure_length = exposure_time
        while remaining_exposure_length > 0:
            if remaining_exposure_length > 1.0:
                time.sleep(1.0)
                remaining_exposure_length -= 1.0
                if abort:
                    return jsonify({"status": "Failure", "message": "Dark aborted."})
            else:
                time.sleep(remaining_exposure_length)
                remaining_exposure_length = 0
        filename = '/data/dark.fits'
        print ("takeDarkFrame finished with filename ", filename, ".")
        return jsonify({"status": "Success", "message": "successfully saved dark frame.", "filename": filename})
    except Exception as e:
        return handle_exception(e)     

@app.route('/takeExposure', methods=['POST'])
def take_exposure():
    global abort
    global exposure_time
    global remaining_exposure_length
    try:
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400
        payload = request.get_json()
        if 'exposure_time' not in payload:
            return jsonify({"status": "Error", "message": "'exposure_time' is required"}), 400
        exposure_time = payload['exposure_time']
        print ("takeExposure invoked with exposure length", exposure_time, " seconds.")
        abort = False
        remaining_exposure_length = exposure_time
        while remaining_exposure_length > 0:
            if remaining_exposure_length > 1.0:
                time.sleep(1.0)
                remaining_exposure_length -= 1.0
                if abort:
                    return jsonify({"status": "Failure", "message": "Exposure aborted."})
            else:
                time.sleep(remaining_exposure_length)
                remaining_exposure_length = 0
        filename='/data/expose.fits'
        print ("takeExposure finished with filename ", filename, ".")
        return jsonify({"status": "Success", "message": "successfully saved exposure.", "filename": filename})
    except Exception as e:
        return handle_exception(e)

@app.route('/setTemperature', methods=['POST'])
def set_camera_temperature():
    try:
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400
        target_temp = request.json['temperature'] 
        print ("setTemperature invoked with target temperature ", target_temp, ".")
        return jsonify({"status": "Success", "message": "Temperature set to {}".format(target_temp)})     
    except Exception as e:
        return handle_exception(e)

@app.route('/getTemperature', methods=['GET'])
def get_temperature():
    try:
        print ("getTemperature invoked.")
        temperature = -10.0
        cooling_status = 'OK'
        cooler_enabled = True
        return jsonify({"status": "Success", "temperature": temperature, "cooling_enabled": cooler_enabled, "cooling_status": cooling_status})
    except Exception as e:
        return handle_exception(e)

@app.route('/setImageWindow', methods=['POST'])
def set_image_window():
    """
    Set the image window for the Andor camera.
    Payload (JSON):
         - horizontal_binning (int): The horizontal binning factor (default: 2).
         - vertical_binning (int): The vertical binning factor (default: 2).
         - horizontal_start (int): The starting horizontal (x-coordinate) of the image window (defaults to the existing value).
         - vertical_start (int): The starting vertical (y-coordinate) of the image window (defaults to the existing value).
         - horizontal_end (int): The horizontal end (x-coordinate) of the image window (defaults to the end of the ccd).
         - vertical_end (int): The vertical end (y-coordinate) of the image window (defaults to the end of the ccd).
    Returns:
         JSON response:
             - {"status": "Success", "message": "Image window set"} on success.
             - {"status": "Error", "message": "Request payload must be JSON"} if the payload is invalid.
             - On error, an appropriate error response with status code 500.
    """
    try:
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400
        payload = request.json
        horizontal_binning = int(payload.get('horizontal_binning', 2))
        horizontal_start = int(payload.get('horizontal_start', 1))
        horizontal_end = int(payload.get('horizontal_end', 2048))
        vertical_binning = int(payload.get('vertical_binning', 2))
        vertical_start = int(payload.get('vertical_start', 1))
        vertical_end = int(payload.get('vertical_end', 2048))
        return jsonify({"status": "Success", "message": "Image window set"})
    except Exception as e:
        return handle_exception(e)

@app.route('/getExposureProgress', methods=['GET'])
def get_exposure_progress():
    global exposure_time
    global remaining_exposure_length
    """
     Retrieve the progress of the current exposure on the Andor camera.
     Returns:
         JSON response:
             - { "status": "Success",
                 "time_elapsed": <time_since_exposure_start>,
                 "time_remaining": <time_left_until_exposure_end>,
                 "exposure_time": <the_requested_exposure_time> }
             on success.
             - On error, an appropriate error response with status code 500.
     """
    try:
        time_elapsed = exposure_time - remaining_exposure_length
        return jsonify({"status": "Success", "time_elapsed": time_elapsed, "time_remaining": remaining_exposure_length, "exposure_time": exposure_time})
    except Exception as e:
        return handle_exception(e) 

     
@app.route('/abortExposure', methods=['POST'])
def abort_exposure():
    global abort
    try:
        abort = True
        return jsonify({"status": "Success", "message": "Exposure aborted"})
    except Exception as e:
        return handle_exception(e)

@app.route('/shutDown', methods=['POST'])
def shut_down():
    try: 
        return jsonify({"status": "Success", "message": "Camera shut down"}) 
    except Exception as e:
        return handle_exception(e)

@app.route('/clearHeaderKeywords', methods=['POST'])
def clear_header_keywords():
    """
    Clear all additional header keywords from the Andor camera.
     Returns:
         JSON response:
             - {"status": "Success", "message": "Header keywords cleared"} on success. 
            - On error, an appropriate error response with status code 500.
     """
    try:
        return jsonify({"status": "Success", "message": "Header keywords cleared"})
    except Exception as e:
        return handle_exception(e) 


@app.route('/setHeaderKeywords', methods=['POST'])
def set_header_keywords():
    """
    In this endpoint, multiple header keywords may be set at once.
    The request payload should be a JSON object with a single key, 'keywords', whose value is a list of dictionaries.
    Each dictionary should have at least two keys, with two other optional ones: 'keyword' and 'value' are required, 
    'comment' and 'units' are optional. The endpoint will add these to any existing ones, overwriting any existing ones with the same keyword.
    Example payload:
    {
        "keywords": 
        [
             {"keyword": "OBSERVER", "value": "John Doe", "comment": "The observer's name"},
             {"keyword": "OBJECT", "value": "M42", "comment": "The target object"},
             {"keyword": "EXPTIME", "value": 10.0, "comment": "Exposure time in seconds", "units": "s"},
             {"keyword": "FILTER", "value": "V"}
         ]
    }
    """
    try:
        # Ensure request is JSON
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400
        keywords = request.json.get('keywords', [])
        if not isinstance(keywords, list):
            return jsonify({"status": "Error", "message": "'keywords' must be a list"}), 400 
        return jsonify({"status": "Success", "message": "Header keywords set"})
    except Exception as e:
        return handle_exception(e)
    
@app.route('/setHeaderKeyword', methods=['POST'])
def set_header_keyword():
    """In this endpoint, a single header keyword may be set.
     The request payload should be a JSON object with info regarding a single FITS keyword.
     It should have at least two keys, with two other optional ones: 'keyword' and 'value' are required,
     'comment' and 'unit' are optional. The endpoint will add this info to any existing ones.
     Example payload:
     {
         "keyword": "EXPTIME",
         "value": 10.0,
         "comment": "Exposure time in seconds",
         "unit": "s"
     }
     """
    try:
        # Ensure request is JSON
        if not request.is_json:
            return jsonify({"status": "Error", "message": "Request payload must be JSON"}), 400
        keyword = request.json
        return jsonify({"status": "Success", "message": "Header keyword set"})
    except Exception as e:
        return handle_exception(e)
    
@app.route('/setCooling', methods=['POST'])
def set_cooling():
    try:
        cooling_state = request.json.get('cooling', True)  # Default to True (cooling ON)
        if cooling_state:
            return jsonify({"status": "Success", "message": "Cooling turned ON"})
        else:
            return jsonify({"status": "Success", "message": "Cooling turned OFF"})
    except Exception as e:
        return handle_exception(e)

@app.route('/getCameraStatus', methods=['GET'])
def get_camera_status():
    try:
        status = 'DRV_IDLE'
        return jsonify({"status": "Success", "camera_status": status})
    except Exception as e:
        return handle_exception(e)        

def handle_exception(e):
    traceback.print_exc()
    return jsonify({"status": "Error", "message": str(e)}), 500

if __name__ == '__main__':
    try:
        app.run(host='0.0.0.0', port=5100)
    except Exception as e:
        print("Error running the server:", str(e))
