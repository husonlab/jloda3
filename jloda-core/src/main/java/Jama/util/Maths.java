/*
 * third party
 * JAMA's initial design, as well as this reference implementation, was developed by

Joe Hicklin
Cleve Moler
Peter Webb	... from The MathWorks	 	Ronald F. Boisvert
Bruce Miller
Roldan Pozo
Karin Remington	... from NIST

Copyright Notice
This software is a cooperative product of The MathWorks and the National Institute of Standards and Technology (NIST)
which has been released to the public domain. Neither The MathWorks nor NIST assumes any responsibility whatsoever for its
use by other parties, and makes no guarantees, expressed or implied, about its quality, reliability, or any other characteristic.

 */

package Jama.util;

public class Maths {

	/**
	 * sqrt(a^2 + b^2) without under/overflow.
	 **/

	public static double hypot(double a, double b) {
		double r;
		if (Math.abs(a) > Math.abs(b)) {
			r = b / a;
			r = Math.abs(a) * Math.sqrt(1 + r * r);
		} else if (b != 0) {
			r = a / b;
			r = Math.abs(b) * Math.sqrt(1 + r * r);
		} else {
			r = 0.0;
		}
		return r;
	}
}
