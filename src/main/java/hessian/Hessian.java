package hessian;

import ij.IJ;
import ij.ImagePlus;
import util.IImage;

public class Hessian {

	private IImage image;

	private double[]   e = new double[3];
	private double[][] v = new double[3][3];

	public Hessian(ImagePlus imp) {
		image = new IImage(imp);
	}

	public ImagePlus[] eigenvalueTransformed() {
		return eigenvalueTransformed(null);
	}

	public ImagePlus[] eigenvalueTransformed(ImagePlus[] existing) {
		IImage res0 = new IImage(existing != null ? existing[0] : IJ.createImage("e0", image.w, image.h, image.d, 32));
		IImage res1 = new IImage(existing != null ? existing[1] : IJ.createImage("e1", image.w, image.h, image.d, 32));
		IImage res2 = new IImage(existing != null ? existing[2] : IJ.createImage("e2", image.w, image.h, image.d, 32));

		double[] eigenvalues    = new double[3];
		double[][] eigenvectors = new double[3][3];
		for(int z = 0; z < image.d; z++) {
			for(int y = 0; y < image.h; y++) {
				for(int x = 0; x < image.w; x++) {
					evaluate(x, y, z);
					get(eigenvalues, eigenvectors);
					if(Math.abs(eigenvalues[0]) > 0.1) {
						res0.setf(x, y, z, (float)eigenvalues[0]);
						res1.setf(x, y, z, (float)eigenvalues[1]);
						res2.setf(x, y, z, (float)eigenvalues[2]);
					}
				}
			}
			IJ.showProgress(z, image.d);
		}
		res0.orig.setCalibration(image.orig.getCalibration().copy());
		res1.orig.setCalibration(image.orig.getCalibration().copy());
		res2.orig.setCalibration(image.orig.getCalibration().copy());
		return new ImagePlus[] {res0.orig, res1.orig, res2.orig};
	}

	public void evaluate(int px, int py, int pz) {
		double[][] matrix = calculateHessianMatrix(px, py, pz);
		calculateEigenvalues(matrix);
	}

	public void get(double[] eigenvalues, double[][] eigenvectors) {
		System.arraycopy(e,    0, eigenvalues,     0, 3);
		System.arraycopy(v[0], 0, eigenvectors[0], 0, 3);
		System.arraycopy(v[1], 0, eigenvectors[1], 0, 3);
		System.arraycopy(v[2], 0, eigenvectors[2], 0, 3);
	}

	public double[][] calculateHessianMatrix(int px, int py, int pz) {

		int radius = 1;

		double[][] hessianMatrix = new double[3][3];

        int xc = px;
        int xp = xc - radius;
        int xn = xc + radius;

        int yc = py;
        int yp = yc - radius;
        int yn = yc + radius;

        int zc = pz;
        int zp = zc - radius;
        int zn = zc + radius;

        float temp = 2 * image.getf(xc, yc, zc);

        // xx
        hessianMatrix[0][0] = (image.getf(xn, yc, zc) - temp + image.getf(xp, yc, zc)) / image.pw;
        // yy
        hessianMatrix[1][1] = (image.getf(xc, yn, zc) - temp + image.getf(xc, yp, zc)) / image.ph;
        // zz
        hessianMatrix[2][2] = (image.getf(xc, yc, zn) - temp + image.getf(xc, yc, zp)) / image.pd;

        // xy
        hessianMatrix[0][1] = hessianMatrix[1][0] =
            (
                (image.getf(xn, yn, zc) - image.getf(xp, yn, zc)) / (2 * image.pw)
                -
                (image.getf(xn, yp, zc) - image.getf(xp, yp, zc)) / (2 * image.pw)
                ) / (2 * image.ph);

        // xz
        hessianMatrix[0][2] = hessianMatrix[2][0] =
            (
                (image.getf(xn, yc, zn) - image.getf(xp, yc, zn)) / (2 * image.pw)
                -
                (image.getf(xn, yc, zp) - image.getf(xp, yc, zp)) / (2 * image.pw)
                ) / (2 * image.pd);

        // yz
        hessianMatrix[1][2] = hessianMatrix[2][1] =
            (
                (image.getf(xc, yn, zn) - image.getf(xc, yp, zn)) / (2 * image.ph)
                -
                (image.getf(xc, yn, zp) - image.getf(xc, yp, zp)) / (2 * image.ph)
                ) / (2 * image.pd);

        return hessianMatrix;
    }

	private static final double TWOPI = 2 * Math.PI;

	// descending order
	public void calculateEigenvalues(double[][] eigenMatrix) {

	    Eigen.solveSymmetric33Fast(eigenMatrix, v, e);


//		final double fhxx = eigenMatrix[0][0];
//		final double fhxy = eigenMatrix[0][1];
//		final double fhxz = eigenMatrix[0][2];
//		final double fhyy = eigenMatrix[1][1];
//		final double fhyz = eigenMatrix[1][2];
//		final double fhzz = eigenMatrix[2][2];
//		final double a = -(fhxx + fhyy + fhzz);
//		final double b = fhxx*fhyy + fhxx*fhzz + fhyy*fhzz - fhxy*fhxy - fhxz*fhxz - fhyz*fhyz;
//		final double c = fhxx*(fhyz*fhyz - fhyy*fhzz) + fhyy*fhxz*fhxz + fhzz*fhxy*fhxy - 2*fhxy*fhxz*fhyz;
//		final double q = (a*a - 3*b)/9;
//		final double r = (a*a*a - 4.5*a*b + 13.5*c)/27;
//		final double sqrtq = (q > 0) ? Math.sqrt(q) : 0;
//		final double sqrtq3 = sqrtq*sqrtq*sqrtq;
//		double h0, h1, h2;
//		if (sqrtq3 == 0) {
//			e0 = e1 = e2 = 0;
//			v0[0] = v0[1] = v0[2] = 0;
//			v1[0] = v1[1] = v1[2] = 0;
//			v2[0] = v2[1] = v2[2] = 0;
//			return;
//		}
//
//		final double rsqq3 = r/sqrtq3;
//		final double angle = (rsqq3*rsqq3 <= 1) ? Math.acos(rsqq3) : Math.acos(rsqq3 < 0 ? -1 : 1);
//		h0 = -2*sqrtq*Math.cos(angle/3) - a/3;
//		h1 = -2*sqrtq*Math.cos((angle + TWOPI)/3) - a/3;
//		h2 = -2*sqrtq*Math.cos((angle - TWOPI)/3) - a/3;
//
//		solve(fhxx, fhxy, fhxz, fhyy, fhyz, fhzz, h0, v0);
//		solve(fhxx, fhxy, fhxz, fhyy, fhyz, fhzz, h1, v1);
//		solve(fhxx, fhxy, fhxz, fhyy, fhyz, fhzz, h2, v2);


		// sort descending
		double tmpe;
		double[] tmpv;
		if (Math.abs(e[0]) < Math.abs(e[1])) {
			tmpe = e[0]; e[0] = e[1]; e[1] = tmpe;
			tmpv = v[0]; v[0] = v[1]; v[1] = tmpv;
		}
		if (Math.abs(e[1]) < Math.abs(e[2])) {
			tmpe = e[1]; e[1] = e[2]; e[2] = tmpe;
			tmpv = v[1]; v[1] = v[2]; v[2] = tmpv;
		}
		if (Math.abs(e[0]) < Math.abs(e[1])) {
			tmpe = e[0]; e[0] = e[1]; e[1] = tmpe;
			tmpv = v[0]; v[0] = v[1]; v[1] = tmpv;
		}

		normalize(v[0]);
		normalize(v[1]);
		normalize(v[2]);

		e[0] = e[0] > -0 ? 0 : e[0];
		e[1] = e[1] > -0 ? 0 : e[1];
		e[2] = e[2] > -0 ? 0 : e[2];
	}

	private static final void normalize(double[] v) {
		double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		v[0] /= n;
		v[1] /= n;
		v[2] /= n;
	}
}
