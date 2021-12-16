package hessian;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.TextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

import hessian.DoubleSlider.Listener;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.plugin.ChannelSplitter;
import ij.plugin.GaussianBlur3D;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.StackStatistics;

public class Interactive_Hessian_Segmentation implements PlugInFilter {

	public static void main(String...args) {
		new ij.ImageJ();
		// ImagePlus imp = IJ.openImage("D:\\jgrosch\\4-18_Shank2_Bassoon_Tuj1_apo_40x_11-ApoTome_C1.tif");
		ImagePlus imp = IJ.openImage("D:/cgaupp/lightsheet/quantification/14-39-32_test_gut_UltraII_C00_xyz-Table Z0000-crop.tif");
		imp.show();
		Interactive_Hessian_Segmentation is = new Interactive_Hessian_Segmentation();
		is.setup("", imp);
		is.run(null);
	}

	private ImagePlus image;
	private ImagePlus output;
	private double[][] minmax;
	private ImagePlus[] hessians;
	private DoubleSlider[] sliders;
	private DoubleSlider thresholdSlider;
	private double[] thresholdMinmax;
	private double sigma = 1;

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		return DOES_8G | DOES_16 | DOES_32;
	}

	private void arrangeWindows() {
		int availableWidth = (int)output.getWindow().getGraphicsConfiguration().getBounds().getWidth();
		int tw = availableWidth / 4;
		int th = tw * output.getHeight() / output.getWidth();
		int x = 0;
		ImagePlus tmp;
		ImageWindow win;
		tmp = hessians[0]; win = tmp.getWindow(); win.setLocationAndSize(x, 0, tw, th); x += win.getWidth();
		tmp = hessians[1]; win = tmp.getWindow(); win.setLocationAndSize(x, 0, tw, th); x += win.getWidth();
		tmp = hessians[2]; win = tmp.getWindow(); win.setLocationAndSize(x, 0, tw, th); x += win.getWidth();
		tmp = output     ; win = tmp.getWindow(); win.setLocationAndSize(x, 0, tw, th); x += win.getWidth();

		IJ.getInstance().toFront();
	}

	@Override
	public void run(ImageProcessor ip) {
		hessians = computeHessianImages(sigma, false, image, null);
		// suppressPositive(hessians);
		// sortEigenvalues(hessians);
		for(ImagePlus impt : hessians) {
			impt.setSlice(impt.getNSlices() / 2);
			ImageProcessor tmp = impt.getProcessor();
			tmp.setRoi(1, 1, impt.getWidth() - 2, impt.getHeight() - 2);
			tmp = tmp.crop();
			tmp.resetMinAndMax();
			impt.getProcessor().setMinAndMax(tmp.getMin(), tmp.getMax());
			impt.show();
		}

		output = IJ.createImage("Segmentation",
				image.getBitDepth() + "-bit black composite-mode",
				hessians[0].getWidth(),
				hessians[0].getHeight(),
				2, // channels
				hessians[0].getStackSize(),
				1);
		output.setCalibration(image.getCalibration().copy());
		initializeOutput();
		final CompositeImage ci = (CompositeImage)output;
		ci.setChannelLut(LUT.createLutFromColor(Color.WHITE), 1);
		ci.setC(1);
		ci.setDisplayRange(image.getDisplayRangeMin(), image.getDisplayRangeMax());
		ci.setC(2);
		ci.setDisplayRange(0, 255);

		ci.setChannelLut(LUT.createLutFromColor(Color.RED), 2);
		ci.setZ(ci.getNSlices() / 2);
		output.show();
		arrangeWindows();


		sliders = new DoubleSlider[hessians.length];
		minmax = new double[hessians.length][2];
		thresholdMinmax = new double[2];

		GenericDialog gd = new GenericDialog("Interactive Hessian Segmentation");
		gd.addNumericField("sigma", 1, 2);
		final TextField tf = (TextField)gd.getNumericFields().firstElement();
		tf.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				double sigmaTmp = Double.parseDouble(tf.getText());
				if(sigmaTmp == sigma)
					return;
				sigma = sigmaTmp;
				new Thread() {
					@Override
					public void run() {
						try {

							GenericDialog gd = new GenericDialog("");
							gd.addMessage("Please wait while hessians are recalculated");
							gd.setModal(false);
							gd.showDialog();
							recalculateHessians(image, sigma, hessians);
							for(int i = 0; i < hessians.length; i++) {
								ImagePlus hessian = hessians[i];
								hessian.updateAndDraw();
								StackStatistics stat = new StackStatistics(hessian);
								sliders[i].setMinAndMax(stat.min, stat.max);
							}
							calculateOutput();
							output.updateAndDraw();
							gd.dispose();
						} catch(NumberFormatException ex) {}
					}
				}.start();
			}
		});
		for(int i = 0; i < hessians.length; i++) {
			final int j = i;
			ImagePlus hessian = hessians[i];
			StackStatistics stat = new StackStatistics(hessian);
			minmax[i][0] = stat.min;
			minmax[i][1] = stat.max;
			sliders[i] = new DoubleSlider("Eig.V. " + (i + 1), stat.min, stat.max, stat.min, stat.max);
			gd.addPanel(sliders[i], GridBagConstraints.EAST, new Insets(5, 5, 5, 5));
			sliders[i].addListener(new Listener() {
				@Override
				public void valuesChanged(double min, double max) {
					minmax[j][0] = min;
					minmax[j][1] = max;
					calculateOutput();
					output.updateAndDraw();
				}
			});
		}
		StackStatistics stat = new StackStatistics(image);
		thresholdMinmax[0] = stat.min;
		thresholdMinmax[1] = stat.max;
		thresholdSlider = new DoubleSlider("Threshold", stat.min, stat.max, stat.min, stat.max);
		gd.addPanel(thresholdSlider, GridBagConstraints.EAST, new Insets(5, 5, 5, 5));
		thresholdSlider.addListener(new Listener() {
			@Override
			public void valuesChanged(double min, double max) {
				thresholdMinmax[0] = min;
				thresholdMinmax[1] = max;
				calculateOutput();
				output.updateAndDraw();
			}
		});

		gd.setModal(false);
		gd.showDialog();
		gd.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				ImagePlus[] channelImages = ChannelSplitter.split(ci);
				ImagePlus segmentation = channelImages[1];
				output.close();
				for(ImagePlus imp : hessians)
					imp.close();
				segmentation.setLut(LUT.createLutFromColor(Color.white));
				segmentation.setCalibration(image.getCalibration().copy());
				segmentation.setStack(
						segmentation.getStack().crop(
								1, 1, 1,
								segmentation.getWidth()   - 2,
								segmentation.getHeight()  - 2,
								segmentation.getNSlices() - 2));
				segmentation.show();
			}
		});
	}

	public void initializeOutput() {
		for(int z = 0; z < image.getStackSize(); z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1).duplicate();
			int outidx = output.getStackIndex(1, z + 1, 1);
			output.getStack().getProcessor(outidx).insert(ip, 0, 0);
		}
	}

	public void calculateOutput() {
		for(int h = 0; h < minmax.length; h++) {
			System.out.println(Arrays.toString(minmax[h]));
		}
		for(int z = 0; z < image.getStackSize(); z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1);
			int outidx = output.getStackIndex(2, z + 1, 1);
			ImageProcessor op = output.getStack().getProcessor(outidx);
			ImageProcessor[] hips = new ImageProcessor[hessians.length];
			for(int i = 0; i < hips.length; i++)
				hips[i] = hessians[i].getStack().getProcessor(z + 1);

			int wh = hips[0].getWidth() * hips[0].getHeight();
			for(int i = 0; i < wh; i++) {
				double v = ip.getf(i);
				boolean fg = v >= thresholdMinmax[0] && v <= thresholdMinmax[1];
				if(fg) {
					for(int h = 0; h < hips.length; h++) {
						v = hips[h].getf(i);
						if(v < minmax[h][0] || v > minmax[h][1]) {
							fg = false;
							break;
						}
					}
				}
				op.setf(i, fg ? 255 : 0);
			}
		}
	}

	public static ImagePlus[] computeHessianImages(final double sigma,
		final boolean absolute, ImagePlus imp, ImagePlus[] existing) {
//
//		final Image img = Image.wrap(imp);
//		final Aspects aspects = img.aspects();
//
//		final Image newimg = new FloatImage(img);
//		final Hessian hessian = new Hessian();
//
//		final Vector<Image> hessianImages = hessian.run(newimg, sigma, absolute);
//
//		final int nrimgs = hessianImages.size();
//		for (int i=0; i<nrimgs; ++i)
//			hessianImages.get(i).aspects(aspects);
//
//		final ImagePlus[] result = new ImagePlus[nrimgs];
//		for(int i = 0; i < nrimgs; i++)
//			result[i] = hessianImages.get(i).imageplus();
//		return result;

		imp = imp.duplicate();
		GaussianBlur3D.blur(imp, sigma, sigma, sigma);
		Hessian hessian = new Hessian(imp);
		ImagePlus[] res = hessian.eigenvalueTransformed(existing);
		return res;
	}

	public static ImagePlus[] recalculateHessians(ImagePlus image, double sigma, ImagePlus[] existing) {
		ImagePlus[] hessians = computeHessianImages(sigma, false, image, existing);
//		suppressPositive(hessians);
//		sortEigenvalues(hessians);
		return hessians;
	}

	public static void suppressPositive(ImagePlus[] hessians) {
		for(ImagePlus hi : hessians) {
			int w = hi.getWidth(), h = hi.getHeight(), d = hi.getStackSize();
			int wh = w * h;

			for(int z = 0; z < d; z++) {
				ImageProcessor ip = hi.getStack().getProcessor(z + 1);
				for(int i = 0; i < wh; i++) {
					float e0 = ip.getf(i);
					if(e0 > -0.1)
						ip.setf(i, 0);
				}
			}
		}
	}

	// ascending
	public static void sortEigenvalues(ImagePlus[] hessians) {
		if(hessians.length == 3)
			sortEigenvalues3(hessians);
		else if(hessians.length == 2)
			sortEigenvalues2(hessians);
	}

	public static void sortEigenvalues3(ImagePlus[] hessians) {
		ImagePlus h0 = hessians[0];
		ImagePlus h1 = hessians[1];
		ImagePlus h2 = hessians[2];

		int d = h0.getStackSize();
		int wh = h0.getWidth() * h0.getHeight();

		for(int z = 0; z < d; z++) {
			ImageProcessor h0p = h0.getStack().getProcessor(z + 1);
			ImageProcessor h1p = h1.getStack().getProcessor(z + 1);
			ImageProcessor h2p = h2.getStack().getProcessor(z + 1);
			for(int i = 0; i < wh; i++) {
				float[] sorted = sortAbsoluteValues(h0p.getf(i), h1p.getf(i), h2p.getf(i));
				h0p.setf(i, sorted[0]);
				h1p.setf(i, sorted[1]);
				h2p.setf(i, sorted[2]);
			}
		}
	}

	public static void sortEigenvalues2(ImagePlus[] hessians) {
		ImagePlus h0 = hessians[0];
		ImagePlus h1 = hessians[1];

		int d = h0.getStackSize();
		int wh = h0.getWidth() * h0.getHeight();

		for(int z = 0; z < d; z++) {
			ImageProcessor h0p = h0.getStack().getProcessor(z + 1);
			ImageProcessor h1p = h1.getStack().getProcessor(z + 1);
			for(int i = 0; i < wh; i++) {
				float[] sorted = sortAbsoluteValues(h0p.getf(i), h1p.getf(i));
				h0p.setf(i, sorted[0]);
				h1p.setf(i, sorted[1]);
			}
		}
	}

	// ascending
	public static float[] sortAbsoluteValues(float e0, float e1) {
		float e0c = Math.abs(e0);
        float e1c = Math.abs(e1);

        float[] ev = new float[2];

        if(e0c <= e1c) {
        	ev[0] = e0;
        	ev[1] = e1;
        } else {
        	ev[0] = e1;
        	ev[1] = e0;
        }
        return ev;
	}

	// ascending
	public static float[] sortAbsoluteValues(float e0, float e1, float e2) {
		float e0c = Math.abs(e0);
        float e1c = Math.abs(e1);
        float e2c = Math.abs(e2);

        float[] ev = new float[3];

        /* This should sort a, b and c with the minimum number of
           comparisons - it's not necessarily faster than Arrays.sort,
           but we may want to reorder the evectors with them, in which
           case it would be. */

		if (e0c <= e1c) {
			if (e1c <= e2c) {
				ev[0] = e0;
				ev[1] = e1;
				ev[2] = e2;
			} else {
				if (e0c <= e2c) {
					ev[0] = e0;
					ev[1] = e2;
					ev[2] = e1;
				} else {
					ev[0] = e2;
					ev[1] = e0;
					ev[2] = e1;
				}
			}
		} else {
			if (e0c <= e2c) {
				ev[0] = e1;
				ev[1] = e0;
				ev[2] = e2;
			} else {
				if (e1c <= e2c) {
					ev[0] = e1;
					ev[1] = e2;
					ev[2] = e0;
				} else {
					ev[0] = e2;
					ev[1] = e1;
					ev[2] = e0;
				}
			}
		}
		return ev;
	}
}
