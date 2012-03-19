package com.bitfire.uracer.postprocessing;

import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/**
 * Encapsulates a ping-pong buffer.
 *
 * @author manuel
 *
 */
public class PingPongBuffer
{
	public FrameBuffer buffer1, buffer2;
	public Texture texture1, texture2;
	public int width, height;
	public final boolean ownResources;

	// internal state
	private Texture texResult, texSrc;
	private FrameBuffer bufResult, bufSrc;
	private boolean writeState, pending1, pending2;

	// save/restore state
	private final FrameBuffer owned1, owned2;
	private FrameBuffer ownedResult, ownedSource;
	private int ownedW, ownedH;

	/**
	 * Creates a new ping-pong buffer and owns the resources.
	 */
	public PingPongBuffer( int width, int height, Format frameBufferFormat, boolean hasDepth )
	{
		ownResources = true;
		set( owned1 = new FrameBuffer( frameBufferFormat, width, height, hasDepth ), owned2 = new FrameBuffer( frameBufferFormat, width, height, hasDepth ) );
	}

	/* Creates a new ping-pong buffer with the given buffers.
	 */
	public PingPongBuffer( FrameBuffer buffer1, FrameBuffer buffer2 )
	{
		ownResources = false;
		owned1 = owned2 = null;
		set( buffer1, buffer2 );
	}

	/**
	 * An instance of this object can also be used to manipulate some other
	 * externally-allocated buffers, applying just the same ping-ponging behavior.
	 *
	 * If this instance of the object was owning the resources, they will be preserved
	 * and will be restored by a {@link #reset()} call.
	 *
	 * @param buffer1 the first buffer
	 * @param buffer2 the second buffer
	 */
	public void set( FrameBuffer buffer1, FrameBuffer buffer2 )
	{
		if(ownResources)
		{
			ownedResult = bufResult;
			ownedSource = bufSrc;
			ownedW = width;
			ownedH = height;
		}

		this.buffer1 = buffer1;
		this.buffer2 = buffer2;
		width = this.buffer1.getWidth();
		height = this.buffer1.getHeight();
		rebind();
	}

	/**
	 * Restore the previous buffers if the instance was owning resources.
	 */
	public void reset()
	{
		if(ownResources)
		{
			buffer1 = owned1;
			buffer2 = owned2;
			width = ownedW;
			height = ownedH;
			bufResult = ownedResult;
			bufSrc = ownedSource;
		}
	}

	/**
	 * Free the resources, if any.
	 */
	public void dispose()
	{
		if(ownResources)
		{
			// make sure we delete what we own
			// if the caller didn't call {@link #reset()}
			owned1.dispose();
			owned2.dispose();
		}
	}

	/**
	 * When needed graphics memory could be invalidated so buffers should be rebuilt.
	 */
	public void rebind()
	{
		texture1 = buffer1.getColorBufferTexture();
		texture2 = buffer2.getColorBufferTexture();
		restore();
	}

	// internal use
	// set the object to a known initial state
	private void restore()
	{
		pending1 = pending2 = false;
		writeState = true;

		texSrc = texture1; bufSrc = buffer1;
		texResult = texture2; bufResult = buffer2;
	}

	/**
	 * Ensures the initial buffer state is always the same before starting ping-ponging.
	 */
	public void begin()
	{
		rebind();
	}

	/**
	 * Starts and/or continue ping-ponging, begin capturing on the next available buffer,
	 * returns the result of the previous {@link #capture()} call.
	 *
	 * @return the Texture containing the result.
	 */
	public Texture capture()
	{
		endPending();

		if( writeState )
		{
			// set src
			texSrc = texture1;
			bufSrc = buffer1;

			// set result
			texResult = texture2;
			bufResult = buffer2;

			// write to other
			pending2 = true;
			buffer2.begin();
		} else
		{
			texSrc = texture2;
			bufSrc = buffer2;

			texResult = texture1;
			bufResult = buffer1;

			pending1 = true;
			buffer1.begin();
		}

		writeState = !writeState;
		return texSrc;
	}

	/**
	 * Finishes ping-ponging, must always be called after a call to {@link #capture()}
	 */
	public void end()
	{
		endPending();
	}

	/**
	 * @return the source texture of the current ping-pong chain.
	 */
	public Texture getSouceTexture()
	{
		return texSrc;
	}

	/**
	 * @return the source buffer of the current ping-pong chain.
	 */
	public FrameBuffer getSourceBuffer()
	{
		return bufSrc;
	}

	/**
	 * @return the result's texture of the latest {@link #capture()}.
	 */
	public Texture getResultTexture()
	{
		return texResult;
	}

	/**
	 * @return Returns the result's buffer of the latest {@link #capture()}.
	 */
	public FrameBuffer getResultBuffer()
	{
		return bufResult;
	}

	// internal use
	// finish writing to the buffers, mark as not pending anymore.
	private void endPending()
	{
		if( pending1 )
		{
			buffer1.end();
			pending1 = false;
		}

		if( pending2 )
		{
			buffer2.end();
			pending2 = false;
		}
	}
}