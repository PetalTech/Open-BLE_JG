# OpenBCIBLE
Android app for testing BLE connection to OpenBCI Cyton and Ganglion Boards

This project is a extension of https://github.com/googlesamples/android-BluetoothLeGatt configured for the OpenBCI boards (http://openbci.com/) namely Ganglion and Cyton.

The app should show the available boards, and correctly identify the SERVICE, SEND and RECEIVE characteristics for the boards.
Click the SEND characteristic to toggle-write 'b' or 's' to the board.


JG NOTES: 12.30.17



================Notes on file:

There are a lot of non-needed and non-used java files here, its my workspace, so ignore most of them sorry about that. Code is terrible too forgive me lol.

The microvolt conversion is found in DeviceControlActivity from line 231 to 663. This happens during live gathering of ganglion data.

The LocalDataActivity file is what Jon helped with. It reads single-column data from a file (that has ALREADY been converted to microvolts) so we can mess around with processing steps off of previously recorded data without using the ganglion.

In LocalDataActivity I include my bandpass filter as well as a fast fourier transform, both working perfect.

I'm still working on bandpowerextractor.

================Next Steps:

First we need to convert bytes to microvolts. 
	This involves parsing the delta-compressed data from http://docs.openbci.com/Hardware/08-Ganglion_Data_Format.
	My steps include: 
		1. Converting bytes to string
		2. Parsing the strings to create a new string for each channel/sample.
			A. This involves taking the least-significant bit for the sign.
		3. Converting new strings to ints.
		4. Multiplying those ints by the scale factor of the hardware to get microvolts.
			A. E.g.: Scale Factor (Volts/count) = 1.2 Volts * 8388607.0 * 1.5 * 51.0.
Once we get it in microvolts, we find a packet 0 which is sent every second and contains uncompressed data for each channel.
	Then we subtract our microvolt values for the next sample (e.g. sample 1, packet 101) from the uncompressed sample 0.
		This is becuase ganglion only sends deltas or changes from the previous value to increase sampling rate and compress data.
	Finally, we keep subtracting (first do sample 101-0, then 102-this 101-0 value, then 103 minus the value you just got, etc).
		We keep subtracting until we get to the end (e.g. sample 200) and then receive packet 0 again.
			At packet 0 the process starts all over again!

Then we need to create a buffer to hold the last XXX sample points of data to do our processing on it.

Next we need to add a bandpass filter. Add this filter to each incoming samples individually then append into buffer array for testing.

Then we need to port peak detection code from python to apply on this final filtered buffer. Then we have a working brow detection!
