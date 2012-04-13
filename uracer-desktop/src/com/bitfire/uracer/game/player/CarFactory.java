package com.bitfire.uracer.game.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.bitfire.uracer.Art;
import com.bitfire.uracer.Config;
import com.bitfire.uracer.carsimulation.CarModel;
import com.bitfire.uracer.entities.EntityType;
import com.bitfire.uracer.game.collisions.CollisionFilters;
import com.bitfire.uracer.game.player.Car.Aspect;
import com.bitfire.uracer.game.player.Car.InputMode;
import com.bitfire.uracer.utils.Convert;
import com.bitfire.uracer.utils.FixtureAtlas;

public final class CarFactory {
	private CarFactory() {
	}

	public static GhostCar createGhost( Aspect type, CarModel model ) {
		CarRenderer graphics = createCarGraphics( type, model );
		GhostCar ghost = GhostCar.createForFactory( graphics, type, model );
		applyCarPhysics( ghost, EntityType.CarReplay );
		return ghost;
	}

	public static GhostCar createGhost( Car car ) {
		return CarFactory.createGhost( car.getCarAspect(), car.getCarModel() );
	}

	public static Car createPlayer( Aspect carAspect, CarModel model ) {
		CarRenderer graphics = createCarGraphics( carAspect, model );
		Car car = Car.createForFactory( graphics, model, carAspect, InputMode.InputFromPlayer );
		applyCarPhysics( car, EntityType.Car );
		return car;
	}

	private static CarRenderer createCarGraphics( Aspect type, CarModel model ) {
		TextureRegion region = null;

		switch( type ) {
		case OldSkool:
			region = Art.cars.findRegion( "electron" );
			break;

		case OldSkool2:
			region = Art.cars.findRegion( "spider" );
			break;
		}

		return new CarRenderer( model, region );
	}

	private static void applyCarPhysics( Car car, EntityType entityType ) {
		CarModel model = car.getCarModel();
		TextureRegion region = car.getGraphics().getTextureRegion();

		String shapeName = null;
		String shapeRef = null;

		switch( car.getCarAspect() ) {
		case OldSkool:
			region = Art.cars.findRegion( "electron" );
			shapeName = "data/base/electron.shape";
			shapeRef = "../../data-src/base/cars/electron.png";
			break;

		case OldSkool2:
			region = Art.cars.findRegion( "spider" );
			shapeName = "data/base/electron.shape";
			shapeRef = "../../data-src/base/cars/electron.png";
			break;
		}

		// set physical properties and apply shape
		FixtureDef fd = new FixtureDef();
		fd.density = model.density;
		fd.friction = model.friction;
		fd.restitution = model.restitution;

		fd.filter.groupIndex = (short)((entityType == EntityType.Car) ? CollisionFilters.GroupPlayer : CollisionFilters.GroupReplay);
		fd.filter.categoryBits = (short)((entityType == EntityType.Car) ? CollisionFilters.CategoryPlayer : CollisionFilters.CategoryReplay);
		fd.filter.maskBits = (short)((entityType == EntityType.Car) ? CollisionFilters.MaskPlayer : CollisionFilters.MaskReplay);

		if( Config.Debug.TraverseWalls ) {
			fd.filter.groupIndex = CollisionFilters.GroupNoCollisions;
		}

		// apply scaling factors
		Vector2 offset = new Vector2( -model.width / 2f, -model.length / 2f );

		Vector2 ratio = new Vector2( model.width / Convert.px2mt( region.getRegionWidth() ), model.length / Convert.px2mt( region.getRegionHeight() ) );

		// box2d editor "normalization" contemplates just a width-bound ratio..
		// WTF?
		Vector2 factor = new Vector2( Convert.px2mt( region.getRegionWidth() * ratio.x ), Convert.px2mt( region.getRegionWidth() * ratio.y ) );

		FixtureAtlas atlas = new FixtureAtlas( Gdx.files.internal( shapeName ) );
		atlas.createFixtures( car.getBody(), shapeRef, factor.x, factor.y, fd, offset, entityType );

	}
}
