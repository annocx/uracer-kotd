package com.bitfire.uracer.debug;

import java.util.Formatter;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.bitfire.uracer.Art;
import com.bitfire.uracer.Config;
import com.bitfire.uracer.Physics;
import com.bitfire.uracer.URacer;
import com.bitfire.uracer.VersionInfo;

public class Debug
{

	// frame stats
	private static long frameStart;
	private static float physicsTime, renderTime;
	private static Stats gfxStats;

	// box2d
	private static Box2DDebugRenderer b2drenderer;

	// text render
	private static SpriteBatch batch;
	private static StringBuilder sb;
	private static Formatter fmt;
	private static String[] chars = { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", ".,!?:;\"'+-=/\\< " };
	private static Matrix4 topLeftOrigin, identity;

	public static int fontWidth;
	public static int fontHeight;

	private Debug()
	{
	}

	public static void create()
	{
		sb = new StringBuilder();
		fmt = new Formatter( sb, Locale.US );

		fontWidth = fontHeight = 6;
		physicsTime = renderTime = 0;
		b2drenderer = new Box2DDebugRenderer();
		frameStart = System.nanoTime();

		// compute graphics stats size
		float updateHz = 0.2f;
		if( !Config.isDesktop ) updateHz = 1f;
		gfxStats = new Stats( updateHz );

		// y-flip
		topLeftOrigin = new Matrix4();
		topLeftOrigin.setToOrtho( 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), 0, 0, 10 );
		identity = new Matrix4();

		// init statics
		tmp = "";
		a = new Vector2();
		b = new Vector2();
	}

	public static void begin( SpriteBatch batch )
	{
		batch.setTransformMatrix( identity );
		batch.setProjectionMatrix( topLeftOrigin );
		batch.begin();
		Debug.batch = batch;
	}

	public static void end()
	{
		batch.end();
		batch = null;
	}

	public static void dispose()
	{
		b2drenderer.dispose();
		gfxStats.dispose();
	}

	public static void update()
	{
		gfxStats.update();

		long time = System.nanoTime();

		if( time - frameStart > 1000000000 )
		{
			physicsTime = URacer.getPhysicsTime();
			renderTime = URacer.getRenderTime();
			frameStart = time;
		}
	}

	public static void renderFrameStats( float temporalAliasingFactor )
	{
		sb.setLength( 0 );
		drawString(
				fmt.format( "fps: %d, physics: %.06f, graphics: %.06f", Gdx.graphics.getFramesPerSecond(), physicsTime,
						renderTime ).toString(), 0, Gdx.graphics.getHeight() - 6 );

		sb.setLength( 0 );
		drawString( fmt.format( "timemul: x%.02f, step: %.0fHz", Config.PhysicsTimeMultiplier, Config.PhysicsTimestepHz )
				.toString(), 0, Gdx.graphics.getHeight() - 12 );
	}

	public static void renderGraphicalStats( int x, int y )
	{
		batch.draw( gfxStats.getRegion(), x, y );

		sb.setLength( 0 );
		String text = fmt.format( "fps: %d, physics: %.06f, graphics: %.06f", Gdx.graphics.getFramesPerSecond(), physicsTime,
				renderTime ).toString();
		drawString( text, Gdx.graphics.getWidth() - text.length() * fontWidth, Gdx.graphics.getHeight() - fontHeight );
	}

	public static void renderVersionInfo()
	{
		String uRacerInfo = "uRacer " + VersionInfo.versionName;
		drawString( uRacerInfo, Gdx.graphics.getWidth() - uRacerInfo.length() * fontWidth, 0, fontWidth, fontHeight * 2 );
	}

	public static void renderMemoryUsage()
	{
		float oneOnMb = 1f / 1048576f;
		float javaHeapMb = (float)Gdx.app.getJavaHeap() * oneOnMb;
		float nativeHeapMb = (float)Gdx.app.getNativeHeap() * oneOnMb;

		sb.setLength( 0 );
		String memInfo = fmt.format( "java heap = %.04fMB - native heap = %.04fMB", javaHeapMb, nativeHeapMb ).toString();
		drawString( memInfo, (Gdx.graphics.getWidth() - memInfo.length() * fontWidth) / 2, 0 );
	}

	public static void renderB2dWorld( Matrix4 modelViewProj )
	{
		b2drenderer.render( Physics.world, modelViewProj );
	}

	public static void draw( TextureRegion region, int x, int y )
	{
		int width = region.getRegionWidth();
		if( width < 0 ) width = -width;

		batch.draw( region, x, y, width, -region.getRegionHeight() );
	}

	public static void draw( TextureRegion region, int x, int y, int width, int height )
	{
		batch.draw( region, x, y, width, height );
	}

	public static void drawString( String string, int x, int y )
	{
		string = string.toUpperCase();
		for( int i = 0; i < string.length(); i++ )
		{
			char ch = string.charAt( i );
			for( int ys = 0; ys < chars.length; ys++ )
			{
				int xs = chars[ys].indexOf( ch );
				if( xs >= 0 )
				{
					draw( Art.base6[xs][ys + 9], x + i * 6, y );
				}
			}
		}
	}

	public static void drawString( String string, int x, int y, int w, int h )
	{
		string = string.toUpperCase();
		for( int i = 0; i < string.length(); i++ )
		{
			char ch = string.charAt( i );
			for( int ys = 0; ys < chars.length; ys++ )
			{
				int xs = chars[ys].indexOf( ch );
				if( xs >= 0 )
				{
					draw( Art.base6[xs][ys + 9], x + i * w, y, w, h );
				}
			}
		}
	}

	public static int getStatsWidth()
	{
		return gfxStats.getWidth();
	}

	public static int getStatsHeight()
	{
		return gfxStats.getHeight();
	}


	/**
	 * stdout facilities
	 */

	public static void print( String string )
	{
		System.out.println( string );
	}

	public static void print( String format, Object... args )
	{
		Debug.print( String.format( format, args ) );
	}

	private static String tmp;
	private static Vector2 a, b;

	public static void print( ContactImpulse impulse, String label, boolean omitDupes )
	{
		String thisString = String.format( "NI=(%.2f,%.2f) | TI=(%.2f,%.2f)", impulse.getNormalImpulses()[0],
				impulse.getNormalImpulses()[1], impulse.getTangentImpulses()[0], impulse.getTangentImpulses()[1] );

		a.set( impulse.getNormalImpulses()[0], impulse.getNormalImpulses()[1] );
		b.set( impulse.getTangentImpulses()[0], impulse.getTangentImpulses()[1] );

		thisString += String.format( " | NIl=%.2f | TI=%.2f", a.len(), b.len() );

		if( label != null ) thisString = label + ": " + thisString;

		if( !tmp.equals( thisString ) )
		{
			System.out.println( thisString );
			tmp = thisString;
		}
	}

	// fuck java all the way up
	public static void print( ContactImpulse impulse )
	{
		Debug.print( impulse, null, true );
	}

	public static void print( ContactImpulse impulse, String label )
	{
		Debug.print( impulse, label, true );
	}
}
