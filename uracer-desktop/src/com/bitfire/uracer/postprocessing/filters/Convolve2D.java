package com.bitfire.uracer.postprocessing.filters;

import com.bitfire.uracer.postprocessing.PingPongBuffer;

/**
 * Encapsulates a separable (1D+1D=2D) 2D convolution kernel filter
 *
 * @author manuel
 */
public class Convolve2D extends MultipassFilter
{
	public final int radius;
	public final int length; // NxN taps filter, w/ N=length

	public final float[] weights, offsetsHor, offsetsVert;

	private Convolve1D hor, vert;

	public Convolve2D( int radius )
	{
		this.radius = radius;
		length = (radius * 2) + 1;

		hor = new Convolve1D( length );
		vert = new Convolve1D( length, hor.weights );

		weights = hor.weights;
		offsetsHor = hor.offsets;
		offsetsVert = vert.offsets;
	}

	public void dispose()
	{
		hor.dispose();
		vert.dispose();
	}

	public void upload()
	{
		hor.upload();
		vert.upload();
	}

	@Override
	public void render( PingPongBuffer buffer )
	{
		hor.setInput( buffer.capture() ).render();
		vert.setInput( buffer.capture() ).render();
	}
}