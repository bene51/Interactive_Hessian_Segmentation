package util;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class IImage {

		public final int w, h, d;
		public final double pw, ph, pd;
		private final ImageProcessor[] data;
		public final ImagePlus orig;

		public IImage(ImagePlus imp) {
			this.orig = imp;

			this.w = imp.getWidth();
			this.h = imp.getHeight();
			this.d = imp.getStackSize();
			this.data = new ImageProcessor[d];
			for(int z = 0; z < d; z++)
				data[z] = imp.getStack().getProcessor(z + 1);

			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
			pd = cal.pixelDepth;
		}

		public void setf(int x, int y, int z, float v) {
			if(x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d)
				return;
			int i = y * w + x;
			data[z].setf(i, v);
		}

		public void setfNoCheck(int x, int y, int z, float v) {
			int i = y * w + x;
			data[z].setf(i, v);
		}

		public float getf(int x, int y, int z) {
			if(x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d)
				return 0;
			return data[z].getf(x, y);
		}

		public float getfNoCheck(int x, int y, int z) {
			return data[z].getf(x, y);
		}

		public float getf(int z, int i) {
			return data[z].getf(i);
		}

		public double getInterpolated(double x, double y, double z) {
			if(x < 0 || x > w - 1 || y < 0 || y > h - 1 || z < 0 || z > d - 1)
				return 0;

			int lx = (int) x;
			int ly = (int) y;
			int lz = (int) z;
			double xR = 1 + lx - x;
			double yR = 1 + ly - y;
			double zR = 1 + lz - z;

			int channel = orig.getChannel() - 1;
			int frame   = orig.getFrame() - 1;

			ImageProcessor ip0 = data[orig.getStackIndex(channel + 1, lz + 1, frame + 1) - 1];
			ImageProcessor ip1 = data[orig.getStackIndex(channel + 1, lz + 2, frame + 1) - 1];

			int ux = lx + 1, uy = ly + 1;
			if(ux >= w || uy >= h)
				return 0;

			float v000 = ip0.getf(lx, ly);
			float v001 = ip1.getf(lx, ly);
			float v010 = ip0.getf(lx, uy);
			float v011 = ip1.getf(lx, uy);
			float v100 = ip0.getf(ux, ly);
			float v101 = ip1.getf(ux, ly);
			float v110 = ip0.getf(ux, uy);
			float v111 = ip1.getf(ux, uy);

			return xR
					* (yR * (zR * v000 + (1 - zR) * v001) + (1 - yR)
							* (zR * v010 + (1 - zR) * v011))
					+ (1 - xR)
					* (yR * (zR * v100 + (1 - zR) * v101) + (1 - yR)
							* (zR * v110 + (1 - zR) * v111));
		}
}
