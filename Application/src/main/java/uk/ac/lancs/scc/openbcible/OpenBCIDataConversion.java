/* Copyright (c) 2014 OpenBCI
 * See the file license.txt for copying permission.
 * */
package uk.ac.lancs.scc.openbcible;

public class OpenBCIDataConversion {

	//JG FOR GANGLION/MCP3912 chip
	final static float fs_Hz = 200.0f;  //sample rate used by OpenBCI board...set by its MCP3912 code
	final static float MCP3912_Vref = 1.2f;  //reference voltage for MCP3912 chip.  set by its hardware
	//	final static float ADS1299_gain = 24;  //assumed gain setting for ADS1299.  set by its Arduino code: Apparently don't need: see difference between Ganglion/MCP3912 from link in next line and cyton found here: http://docs.openbci.com/Hardware/03-Cyton_Data_Format
	final static float scale_fac_uVolts_per_count = (float) (MCP3912_Vref * 8388607.0 * 1.5 * 51.0);  //JG equation found in MCP3912 hardware or here: http://docs.openbci.com/Hardware/08-Ganglion_Data_Format

	// this function is passed a 3 byte array
	static int interpret24bitAsInt32(byte[] byteArray) {
		//little endian
		int newInt = (
				((0xFF & byteArray[0]) << 16) |
						((0xFF & byteArray[1]) << 8) |
						(0xFF & byteArray[2])
		);
		if ((newInt & 0x00800000) > 0) {
			newInt |= 0xFF000000;
		} else {
			newInt &= 0x00FFFFFF;
		}
		return newInt;
	}

	static float convertByteToMicroVolts(byte[] byteArray) {
		return scale_fac_uVolts_per_count * interpret24bitAsInt32(byteArray);
	}


	//**
	//* Converts a special ganglion 19 bit compressed number
	//*  The compressions uses the LSB, bit 1, as the signed bit, instead of using
	//*  the MSB. Therefore you must not look to the MSB for a sign extension, one
	//*  must look to the LSB, and the same rules applies, if it's a 1, then it's a
	//*  negative and if it's 0 then it's a positive number.
	//* @param threeByteBuffer {Buffer}
	//*  A 3-byte buffer with only 19 bits of actual data.
	//* @return {number} A signed integer.
	//*/
	static int convert19bitAsInt32(byte[] byteArray) {
		int newInt = 0;

		if ((byteArray[2] & 0x01) > 0) {
			// console.log('\t\tNegative number')
			newInt = 0b1111111111111;  //to turn it into an int32
		}

		return (newInt << 19) | (byteArray[0] << 16) | (byteArray[1] << 8) | byteArray[2];
	}

	static float convertByteToMicroVolts19(byte[] byteArray) {
		return scale_fac_uVolts_per_count * convert19bitAsInt32(byteArray);
	}

	public static int getTwosComplement(String binaryInt) {
		//Check if the number is negative.
		//We know it's negative if it starts with a 1
		if (binaryInt.charAt(0) == '1') {
			//Call our invert digits method
			String invertedInt = invertDigits(binaryInt);
			//Change this to decimal format.
			int decimalValue = Integer.parseInt(invertedInt, 2);
			//Add 1 to the curernt decimal and multiply it by -1
			//because we know it's a negative number
			decimalValue = (decimalValue + 1) * -1;
			//return the final result
			return decimalValue;
		} else {
			//Else we know it's a positive number, so just convert
			//the number to decimal base.
			return Integer.parseInt(binaryInt, 2);
		}
	}

	public static String invertDigits(String binaryInt) {
		String result = binaryInt;
		result = result.replace("0", " "); //temp replace 0s
		result = result.replace("1", "0"); //replace 1s with 0s
		result = result.replace(" ", "1"); //put the 1s back in
		return result;
	}
}

