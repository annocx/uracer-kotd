
package com.bitfire.uracer.game.world.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.bitfire.uracer.game.world.WorldDefs.Layer;
import com.bitfire.uracer.game.world.WorldDefs.ObjectGroup;
import com.bitfire.uracer.utils.Convert;
import com.bitfire.uracer.utils.VMath;

public final class MapUtils implements Disposable {
	// cache
	public final Map<String, MapLayer> cachedGroups = new HashMap<String, MapLayer>(10);
	public final Map<String, TiledMapTileLayer> cachedLayers = new HashMap<String, TiledMapTileLayer>(10);

	private TiledMap map;
	private Vector2 worldSizeScaledPx = new Vector2();
	public float scaledTilesize, invScaledTilesize;
	private int mapHeight, tileWidth;

	public MapUtils (TiledMap map, int tileWidth, int mapHeight, Vector2 worldSizeScaledPx, float invZoomFactor) {
		this.map = map;
		this.tileWidth = tileWidth;
		this.mapHeight = mapHeight;
		this.worldSizeScaledPx.set(worldSizeScaledPx);

		scaledTilesize = tileWidth * invZoomFactor;
		invScaledTilesize = 1f / scaledTilesize;
	}

	@Override
	public void dispose () {
		cachedLayers.clear();
	}

	public TiledMapTileLayer getLayer (Layer layer) {
		TiledMapTileLayer cached = cachedLayers.get(layer.mnemonic);
		if (cached == null) {
			cached = (TiledMapTileLayer)map.getLayers().getLayer(layer.mnemonic);
			cachedLayers.put(layer.mnemonic, cached);
		}

		return cached;
	}

	public boolean hasLayer (Layer layer) {
		return getLayer(layer) != null;
	}

	public MapLayer getObjectGroup (ObjectGroup group) {
		MapLayer cached = cachedGroups.get(group.mnemonic);
		if (cached == null) {
			cached = map.getLayers().getLayer(group.mnemonic);
			cachedGroups.put(group.mnemonic, cached);
		}

		return cached;
	}

	public boolean hasObjectGroup (ObjectGroup group) {
		return getObjectGroup(group) != null;
	}

	public static List<Vector2> extractPolyData (float[] vertices) {
		List<Vector2> points = new ArrayList<Vector2>();
		int num_verts = vertices.length;
		for (int i = 0; i < num_verts; i += 2) {
			points.add(new Vector2(vertices[i], vertices[i + 1]));
		}

		return points;
	}

	public static List<Vector2> extractPolyData (String encoded) {
		List<Vector2> ret = new ArrayList<Vector2>();

		if (encoded != null && encoded.length() > 0) {
			String[] pairs = encoded.split(" ");
			for (int j = 0; j < pairs.length; j++) {
				String[] pair = pairs[j].split(",");
				ret.add(new Vector2(Integer.parseInt(pair[0]), Integer.parseInt(pair[1])));
			}
		}

		return ret;
	}

	public Vector2 tileToMt (int tilex, int tiley) {
		return Convert.px2mt(tileToPx(tilex, tiley));
	}

	private Vector2 retTile = new Vector2();

	public Vector2 tileToPx (int tilex, int tiley) {
		retTile.set(tilex * tileWidth, (mapHeight - tiley) * tileWidth);
		return retTile;
	}

	public Vector2 pxToTile (float x, float y) {
		retTile.set(x, y);
		retTile.mul(invScaledTilesize);
		retTile.y = mapHeight - retTile.y;
		VMath.truncateToInt(retTile);
		return retTile;
	}

	private Vector2 retPx = new Vector2();

	public Vector2 mtToTile (float x, float y) {
		retPx.set(Convert.mt2px(x), Convert.mt2px(y));
		retPx = pxToTile(retPx.x, retPx.y);
		return retPx;
	}

	public Vector2 positionFor (Vector2 position) {
		return positionFor(position.x, position.y);
	}

	private Vector2 tmp = new Vector2();

	public Vector2 positionFor (float x, float y) {
		tmp = Convert.scaledPixels(tmp.set(x, y));
		tmp.y = worldSizeScaledPx.y - tmp.y;
		return tmp;
	}

	// TODO unused?
	public float orientationFromDirection (String direction) {
		float ret = 0f;

		if (direction.equals("up")) {
			ret = 0f;
		} else if (direction.equals("right")) {
			ret = 90f;
		} else if (direction.equals("down")) {
			ret = 180f;
		} else if (direction.equals("left")) {
			ret = 270f;
		}

		return ret;
	}
}
