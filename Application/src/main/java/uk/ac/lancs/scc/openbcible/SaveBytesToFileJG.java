/* Copyright (c) 2014 OpenBCI
 * See the file license.txt for copying permission.
 * */

package uk.ac.lancs.scc.openbcible;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SaveBytesToFileJG extends AsyncTask<byte[], Void, Void> {

	final static float MCP3912_Vref = 1.2f;  //reference voltage for MCP3912 chip.  set by its hardware
	//	final static float ADS1299_gain = 24;  //assumed gain setting for ADS1299.  set by its Arduino code: Apparently don't need: see difference between Ganglion/MCP3912 from link in next line and cyton found here: http://docs.openbci.com/Hardware/03-Cyton_Data_Format
	final static float scale_fac_uVolts_per_count = (float) (MCP3912_Vref * 8388607.0 * 1.5 * 51.0);  //JG equation found in MCP3912 hardware or here: http://docs.openbci.com/Hardware/08-Ganglion_Data_Format

	public float channel1 = 0;
	public float lastSample = 0;

	@Override
	protected Void doInBackground(byte[]... arduinoData) {
		// Get file name for current session
		String filename = new String(arduinoData[0]);
		// Store data from RFduino
		byte[] data = arduinoData[1];
		// Store packet data
		// The first byte is the sample counter

		byte[] packetData = Arrays.copyOfRange(data, 1, data.length);
		int packetNumber = data[0] & 0xFF;
		//The value 0x00 indicates to the controlling software that the packet contains
		// Uncompressed Data, that is, there are 4 24bit values taking up the 12 bytes
		// following the packet ID associated with channel 1-4 respectively.
		// These values are legit Ganglion data, and are used to seed the
		// decompression algorithm on the comptuer.
		if (packetNumber == 0) {
			byte[] packetDataZero = Arrays.copyOfRange(data, 1, 4);
			String packetDataZeroS0 = String.format("%8s", Integer.toBinaryString(packetDataZero[0] & 0xFF)).replace(' ', '0');
			String packetDataZeroS1 = String.format("%8s", Integer.toBinaryString(packetDataZero[1] & 0xFF)).replace(' ', '0');
			String packetDataZeroS2 = String.format("%8s", Integer.toBinaryString(packetDataZero[2] & 0xFF)).replace(' ', '0');
			String packetDataZeroSfinal = packetDataZeroS0 + packetDataZeroS1 + packetDataZeroS2;
			//String packetDataZeroSfinalsub = packetDataZeroSfinal.substring(23, 24);
			String packetDataZeroSfinal2 = "00000000" + packetDataZeroSfinal;
			int packetDataZeroInt = Integer.parseInt(packetDataZeroSfinal2, 2);
			//System.out.println("packetDataZero uV: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroSfinal2, 2));

			//lastSample = (scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroSfinal2, 2));
			//System.out.println("lastSample0: " + packetNumber + " " + lastSample);
			//channel1 = Integer.parseInt(packetDataZeroSfinal2, 2);

			// Create the file for the current session
			File directory = new File(Environment.getExternalStorageDirectory(),
					"OpenBCI");
			File file = new File(directory, filename);
			DataOutputStream dos;
			try {
				dos = new DataOutputStream(new FileOutputStream(file.getPath(),
						true));
				dos.writeChars(String.valueOf(packetNumber));    //JG
				dos.writeChars(", ");
				dos.writeChars(Float.toString(scale_fac_uVolts_per_count * Integer.parseInt(packetDataZeroSfinal2, 2)));    //JG
				//dos.writeChars(auxNumberString);
				dos.write(System.getProperty("line.separator").getBytes());
				dos.close();
			} catch (FileNotFoundException e) {
				Log.e("Save to File AsyncTask", "Error finding file");
			} catch (IOException e) {
				Log.e("Save to File AsyncTask", "Error saving data");
			}
		}

		else if (packetNumber > 99 && packetNumber < 201) {
			byte[] packetDataCh1s1 = Arrays.copyOfRange(data, 1, 4);
			String Byte2str0Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[0] & 0xFF)).replace(' ', '0');
			String Byte2str1Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[1] & 0xFF)).replace(' ', '0');
			String Byte2str2Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[2] & 0xFF)).replace(' ', '0');
			String AddStringsCh1s1 = (Byte2str0Ch1s1 + Byte2str1Ch1s1 + Byte2str2Ch1s1);
			String AddStringsCh1s1Sub = AddStringsCh1s1.substring(0, 19);
			//String Byte2str2subfirstCh1s1 = AddStringsCh1s1Sub.substring(0, 1);
			String Byte2str2sublastCh1s1 = AddStringsCh1s1Sub.substring(18, 19);
			//If MSB (first bit) but LSB is 1 (last bit), for Ganglgion this is NEGATIVE
			//so we append a 1 on the begining of the final 19 bit string to make it negative
			//and then send it through the sign thing below
			if (Integer.parseInt(Byte2str2sublastCh1s1) == 1) {         //if LSB last slot=1, it is odd
				String AddStringsFullCh1s1 = "1111111111111" + AddStringsCh1s1Sub; //add a 1 to MSB first slot
				StringBuilder onesComplementBuilder = new StringBuilder();
				for (char bit : AddStringsFullCh1s1.toCharArray()) {
					// if bit is '0', append a 1. if bit is '1', append a 0.
					onesComplementBuilder.append((bit == '0') ? 1 : 0);
				}
				String onesComplement = onesComplementBuilder.toString();
				//System.out.println(onesComplement); // should be the NOT of binString
				int converted = Integer.valueOf(onesComplement, 2);
				// two's complement = one's complement + 1. This is the positive value
				// of our original binary string, so make it negative again.
				int valueCh1s1 = -(converted + 1);
				//System.out.println("MicrovoltsCh1s1: " + packetNumber + " "  + scale_fac_uVolts_per_count*valueCh1s1);

				//System.out.println("lastSampleFirst: " + packetNumber + " " + lastSample);
				//lastSample = (scale_fac_uVolts_per_count*valueCh1s1) + lastSample;
				//System.out.println("lastSampleSecond: " + packetNumber + " " + lastSample);

				// Create the file for the current session
				File directory = new File(Environment.getExternalStorageDirectory(),
						"OpenBCI");
				File file = new File(directory, filename);
				DataOutputStream dos;
				try {
					dos = new DataOutputStream(new FileOutputStream(file.getPath(),
							true));
					dos.writeChars(String.valueOf(packetNumber));    //JG
					dos.writeChars(", ");
					dos.writeChars(Float.toString((scale_fac_uVolts_per_count * valueCh1s1)+lastSample));    //JG
					//dos.writeChars(auxNumberString);
					dos.write(System.getProperty("line.separator").getBytes());
					dos.close();
				} catch (FileNotFoundException e) {
					Log.e("Save to File AsyncTask", "Error finding file");
				} catch (IOException e) {
					Log.e("Save to File AsyncTask", "Error saving data");
				}

			} else {
				String AddStringsFullCh1s1 = "0000000000000" + AddStringsCh1s1Sub;
				//System.out.println("MicrovoltsCh1s1: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s1, 2));

				//System.out.println("lastSampleFirst: " + packetNumber + " " + lastSample);
				//lastSample = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s1, 2)) + lastSample;
				//System.out.println("lastSampleSecond: " + packetNumber + " " + lastSample);

				// Create the file for the current session
				File directory = new File(Environment.getExternalStorageDirectory(),
						"OpenBCI");
				File file = new File(directory, filename);
				DataOutputStream dos;
				try {
					dos = new DataOutputStream(new FileOutputStream(file.getPath(),
							true));
					dos.writeChars(String.valueOf(packetNumber));    //JG
					dos.writeChars(", ");
					dos.writeChars(Float.toString((scale_fac_uVolts_per_count * Integer.parseInt(AddStringsFullCh1s1, 2))+lastSample));    //JG
					//dos.writeChars(auxNumberString);
					dos.write(System.getProperty("line.separator").getBytes());
					dos.close();
				} catch (FileNotFoundException e) {
					Log.e("Save to File AsyncTask", "Error finding file");
				} catch (IOException e) {
					Log.e("Save to File AsyncTask", "Error saving data");
				}

			}

			byte[] packetDataCh1s2 = Arrays.copyOfRange(data, 10, 13);
			String Byte2str0Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[0] & 0xFF)).replace(' ', '0');
			String Byte2str1Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[1] & 0xFF)).replace(' ', '0');
			String Byte2str2Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[2] & 0xFF)).replace(' ', '0');
			String AddStringsCh1s2 = (Byte2str0Ch1s2 + Byte2str1Ch1s2 + Byte2str2Ch1s2);
			String AddStringsCh1s2Sub = AddStringsCh1s2.substring(4, 23);
			//String Byte2str2subfirstCh1s2 = AddStringsCh1s2Sub.substring(0, 1);
			String Byte2str2sublastCh1s2 = AddStringsCh1s2Sub.substring(18, 19);
			//If MSB (first bit) but LSB is 1 (last bit), for Ganglgion this is NEGATIVE
			//so we append a 1 on the begining of the final 19 bit string to make it negative
			//and then send it through the sign thing below
			if (Integer.parseInt(Byte2str2sublastCh1s2) == 1) {         //if LSB last slot=1, it is odd
				String AddStringsFullCh1s2 = "11111" + AddStringsCh1s2Sub; //add a 1 to MSB first slot
				//System.out.println("Signed string: " + AddStringsFullCh1s2);
				StringBuilder onesComplementBuilder = new StringBuilder();
				for (char bit : AddStringsFullCh1s2.toCharArray()) {
					// if bit is '0', append a 1. if bit is '1', append a 0.
					onesComplementBuilder.append((bit == '0') ? 1 : 0);
				}
				String onesComplement = onesComplementBuilder.toString();
				//System.out.println(onesComplement); // should be the NOT of binString
				int converted = Integer.valueOf(onesComplement, 2);
				// two's complement = one's complement + 1. This is the positive value
				// of our original binary string, so make it negative again.
				int valueCh1s2 = -(converted + 1);
				//System.out.println("MicrovoltsCh1s2: " + packetNumber + " "  + scale_fac_uVolts_per_count*valueCh1s2);

				//lastSample = (scale_fac_uVolts_per_count*valueCh1s2) + lastSample;
				//System.out.println("lastSample: " + packetNumber + " " + lastSample);

						// Create the file for the current session
				File directory = new File(Environment.getExternalStorageDirectory(),
						"OpenBCI");
				File file = new File(directory, filename);
				DataOutputStream dos;
				try {
					dos = new DataOutputStream(new FileOutputStream(file.getPath(),
							true));
					dos.writeChars(String.valueOf(packetNumber));    //JG
					dos.writeChars(", ");
					dos.writeChars(Float.toString((scale_fac_uVolts_per_count * valueCh1s2)+lastSample));    //JG
					//dos.writeChars(auxNumberString);
					dos.write(System.getProperty("line.separator").getBytes());
					dos.close();
				} catch (FileNotFoundException e) {
					Log.e("Save to File AsyncTask", "Error finding file");
				} catch (IOException e) {
					Log.e("Save to File AsyncTask", "Error saving data");
				}

			} else {
				String AddStringsFullCh1s2 = "00000" + AddStringsCh1s2Sub;
				//System.out.println("MicrovoltsCh1s2: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s2, 2));

				//lastSample = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s2, 2)) + lastSample;
				//System.out.println("lastSample: " + packetNumber + " " + lastSample);

						// Create the file for the current session
				File directory = new File(Environment.getExternalStorageDirectory(),
						"OpenBCI");
				File file = new File(directory, filename);
				DataOutputStream dos;
				try {
					dos = new DataOutputStream(new FileOutputStream(file.getPath(),
							true));
					dos.writeChars(String.valueOf(packetNumber));    //JG
					dos.writeChars(", ");
					dos.writeChars(Float.toString((scale_fac_uVolts_per_count * Integer.parseInt(AddStringsFullCh1s2, 2))+lastSample));    //JG
					//dos.writeChars(auxNumberString);
					dos.write(System.getProperty("line.separator").getBytes());
					dos.close();
				} catch (FileNotFoundException e) {
					Log.e("Save to File AsyncTask", "Error finding file");
				} catch (IOException e) {
					Log.e("Save to File AsyncTask", "Error saving data");
				}

			}
		}
		return null;
	}
}