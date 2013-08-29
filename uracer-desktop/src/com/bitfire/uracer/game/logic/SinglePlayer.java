
package com.bitfire.uracer.game.logic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.bitfire.postprocessing.PostProcessor;
import com.bitfire.uracer.Input;
import com.bitfire.uracer.URacer;
import com.bitfire.uracer.configuration.Config;
import com.bitfire.uracer.configuration.Storage;
import com.bitfire.uracer.configuration.UserProfile;
import com.bitfire.uracer.game.GameplaySettings;
import com.bitfire.uracer.game.actors.GhostCar;
import com.bitfire.uracer.game.debug.DebugCarEngineVolumes;
import com.bitfire.uracer.game.debug.DebugHelper;
import com.bitfire.uracer.game.debug.DebugHelper.RenderFlags;
import com.bitfire.uracer.game.debug.DebugMusicVolumes;
import com.bitfire.uracer.game.debug.GameTrackDebugRenderer;
import com.bitfire.uracer.game.debug.player.DebugPlayer;
import com.bitfire.uracer.game.logic.gametasks.messager.Message;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Position;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Size;
import com.bitfire.uracer.game.logic.gametasks.sounds.effects.PlayerEngineSoundEffect;
import com.bitfire.uracer.game.logic.gametasks.sounds.effects.PlayerTensiveMusic;
import com.bitfire.uracer.game.logic.replaying.Replay;
import com.bitfire.uracer.game.logic.replaying.ReplayInfo;
import com.bitfire.uracer.game.logic.replaying.ReplayManager.DiscardReason;
import com.bitfire.uracer.game.logic.replaying.ReplayManager.ReplayResult;
import com.bitfire.uracer.game.logic.types.helpers.CameraShaker;
import com.bitfire.uracer.game.rendering.GameRenderer;
import com.bitfire.uracer.game.screens.GameScreensFactory.ScreenType;
import com.bitfire.uracer.game.world.GameWorld;
import com.bitfire.uracer.screen.TransitionFactory.TransitionType;
import com.bitfire.uracer.utils.CarUtils;
import com.bitfire.uracer.utils.Convert;
import com.bitfire.uracer.utils.OrdinalUtils;
import com.bitfire.uracer.utils.ReplayUtils;
import com.bitfire.uracer.utils.URacerRuntimeException;

public class SinglePlayer extends BaseLogic {
	protected DebugHelper debug = null;
	private boolean saving = false;
	private CameraShaker camShaker = new CameraShaker();
	private int selectedBestReplayIdx = -1;
	private ReplayResult lastRecorded = new ReplayResult();

	public SinglePlayer (UserProfile userProfile, GameWorld gameWorld, GameRenderer gameRenderer) {
		super(userProfile, gameWorld, gameRenderer);
		setupDebug(gameRenderer.getPostProcessing().getPostProcessor());
	}

	@Override
	public void dispose () {
		destroyDebug();
		super.dispose();
	}

	private void setupDebug (PostProcessor postProcessor) {
		if (Config.Debug.UseDebugHelper) {
			debug = new DebugHelper(gameWorld, postProcessor, lapManager, this, inputSystem);
			debug.add(new GameTrackDebugRenderer(RenderFlags.TrackSectors, gameWorld.getGameTrack()));
			debug.add(new DebugPlayer(RenderFlags.PlayerCarInfo, gameTasksManager));
			debug.add(new DebugMusicVolumes(RenderFlags.MusicVolumes, (PlayerTensiveMusic)gameTasksManager.sound
				.get(PlayerTensiveMusic.class)));

			PlayerEngineSoundEffect se = ((PlayerEngineSoundEffect)gameTasksManager.sound.get(PlayerEngineSoundEffect.class));
			if (se != null) debug.add(new DebugCarEngineVolumes(RenderFlags.CarEngineVolumes, se.getSoundSet()));
			Gdx.app.debug("Game", "Debug helper initialized");
		}
	}

	private void destroyDebug () {
		if (Config.Debug.UseDebugHelper) {
			debug.dispose();
		}
	}

	private GhostCar findGhostFor (Replay replay) {
		for (int g = 0; g < ghostCars.length; g++) {
			GhostCar ghost = ghostCars[g];
			if (ghost != null && replay != null && ghost.getReplay().getId().equals(replay.getId())) {
				return ghost;
			}
		}

		return null;
	}

	private boolean hasGhostFor (Replay replay) {
		return (findGhostFor(replay) != null);
	}

	@Override
	public void handleExtraInput () {
		Input i = inputSystem;

		if (i.isPressed(Keys.O)) {
			removePlayer();
			restartGame();
			restartAllReplays();
		} else if (i.isPressed(Keys.P)) {
			addPlayer();
			restartGame();
		} else if (i.isPressed(Keys.D)) {
			boolean newstate = !gameRenderer.isDebugEnabled();
			gameRenderer.setDebug(newstate);
			debug.setEnabled(newstate);
		} else if (i.isPressed(Keys.TAB)) {
			// choose next/prev best target
			boolean backward = i.isOn(Keys.SHIFT_LEFT) || i.isOn(Keys.SHIFT_RIGHT);

			// retrieve the ghostcar index whose replay is the next/prev best as this one
			// if (!isWarmUp() && isCurrentLapValid)
			{

				GhostCar prevTarget = getNextTarget();
				int maxreplays = lapManager.getReplaysCount();
				int maxtries = maxreplays;

				GhostCar next = null;
				boolean found = false;

				do {
					if (selectedBestReplayIdx == -1) {
						selectedBestReplayIdx = 0;
					}

					if (backward) {
						selectedBestReplayIdx--;
					} else {
						selectedBestReplayIdx++;
					}

					if (selectedBestReplayIdx < 0) selectedBestReplayIdx = maxreplays - 1;
					if (selectedBestReplayIdx >= maxreplays) selectedBestReplayIdx = 0;

					next = getNextTarget();
					found = true;// next != null && next.hasReplay() && !next.getTrackState().ghostArrived;
				} while (maxtries-- >= 0 && !found);

				// Gdx.app.log("SinglePlayer", "Next target index is #" + selectedBestReplayIdx);

				if (!isWarmUp() && found) {
					if (prevTarget != next && next.isPlaying()) {
						playerTasks.hudPlayer.highlightNextTarget(next);
						gameWorldRenderer.setTopMostGhostCar(next);
					}
				}
			}
		}

	}

	@Override
	public ReplayResult getLastRecordedInfo () {
		return lastRecorded;
	}

	@Override
	public GhostCar getNextTarget () {
		int maxreplays = lapManager.getReplays().size;
		if (maxreplays > 0 && selectedBestReplayIdx >= 0 && selectedBestReplayIdx < maxreplays) {
			return findGhostFor(lapManager.getReplays().get(selectedBestReplayIdx));
		}

		return null;
	}

	private void saveReplay (final Replay replay) {
		if (saving) {
			Gdx.app.log("SinglePlayer", "(already saving, request ignored...");
			return;
		}

		if (replay != null) {
			saving = true;
			Thread savingThread = new Thread(new Runnable() {
				@Override
				public void run () {
					if (replay.save()) {
						Gdx.app.log("SinglePlayer",
							"Replay #" + replay.getShortId() + " saved to \"" + ReplayUtils.getFullPath(replay.getInfo()) + "\"");
					}
				}
			});

			savingThread.start();
			saving = false;
		}
	}

	private float getOutOfTrackFactor () {
		float oot = MathUtils.clamp(getOutOfTrackTimer().elapsed().absSeconds, 0, 1);
		float s = MathUtils.clamp(playerCar.carState.currSpeedFactor * 100f, 0, 1);
		// Gdx.app.log("", "oot=" + oot + ", s=" + s);
		return 0.075f * oot * s;
	}

	@Override
	public void updateCameraPosition (Vector2 positionPx) {
		if (hasPlayer()) {
			// update player's headlights and move the world camera to follows it, if there is a player
			if (gameWorld.isNightMode()) {
				gameWorldRenderer.updatePlayerHeadlights(playerCar);
			}
			positionPx.set(playerCar.state().position);
			positionPx.add(camShaker.compute(getCollisionFactor()));
			positionPx.add(camShaker.compute(getOutOfTrackFactor()));
		} else if (isGhostActive(0)) {
			// FIXME use available/choosen replay
			positionPx.set(getGhost(0).state().position);
		} else {
			// no ghost, no player, WTF?
			positionPx.set(Convert.mt2px(gameWorld.playerStart.position));
		}
	}

	/** Load from disk all the replays for the specified trackId, pruning while loading respecting the ReplayManager.MaxReplays
	 * constant. Any previous Replay will be cleared from the lapManager instance. */
	private int loadReplaysFromDiskFor (String trackId) {
		lapManager.removeAllReplays();

		int reloaded = 0;
		for (FileHandle userdir : Gdx.files.external(Storage.ReplaysRoot + gameWorld.getLevelId()).list()) {
			if (userdir.isDirectory()) {
				for (FileHandle userreplay : userdir.list()) {
					Replay replay = Replay.load(userreplay.path());
					if (replay != null) {
						// add replays even if slower
						ReplayResult ri = lapManager.addReplay(replay);
						if (ri.is_accepted) {
							ReplayUtils.pruneReplay(ri.pruned); // prune if needed
							reloaded++;
							Gdx.app.log("SinglePlayer", "Loaded replay #" + ri.accepted.getShortId());
						} else {

							String msg = "";
							switch (ri.reason) {
							case Null:
								msg = "null replay (" + userreplay.path() + ")";
								break;
							case InvalidMinDuration:
								msg = "invalid lap (" + ri.discarded.getSecondsStr() + "s < " + GameplaySettings.ReplayMinDurationSecs
									+ "s) (#" + ri.discarded.getShortId() + ")";
								break;
							case Invalid:
								msg = "the specified replay is not valid. (" + userreplay.path() + ")";
								break;
							case WrongTrack:
								msg = "the specified replay belongs to another game track (#" + ri.discarded.getShortId() + ")";
								break;
							case Slower:
								msg = "too slow! (#" + ri.discarded.getShortId() + ")";
								ReplayUtils.pruneReplay(ri.discarded);
								break;
							case NotDiscarded:
								break;
							}

							Gdx.app.log("SinglePlayer", "Discarded at loading time, " + msg);
						}
					}
				}
			}
		}

		Gdx.app.log("SinglePlayer", "Building opponents list:");

		for (int g = 0; g < ghostCars.length; g++) {
			ghostCars[g].removeReplay();
		}

		rebindAllReplays();

		int pos = 1;
		for (Replay r : lapManager.getReplays()) {
			Gdx.app.log("SinglePlayer",
				"#" + pos + ", #" + r.getShortId() + ", secs=" + r.getSecondsStr() + ", ct=" + r.getCreationTimestamp());
			pos++;
		}

		Gdx.app.log("SinglePlayer", "Reloaded " + reloaded + " opponents.");
		return reloaded;
	}

	@Override
	public void restartGame () {
		Gdx.app.log("SinglePlayer", "Starting/restarting game");
		super.restartGame();
		messager.show("Restarted", 1.5f, Message.Type.Information, Position.Bottom, Size.Big);
		loadReplaysFromDiskFor(gameWorld.getLevelId());
	}

	@Override
	public void resetGame () {
		Gdx.app.log("SinglePlayer", "Resetting game");
		super.resetGame();
		messager.show("Reset", 1.5f, Message.Type.Information, Position.Bottom, Size.Big);
		loadReplaysFromDiskFor(gameWorld.getLevelId());
	}

	@Override
	public void warmUpStarted () {
		messager.show("Warm up!", 1.5f, Message.Type.Information, Position.Top, Size.Big);
	}

	@Override
	public void warmUpCompleted () {
		messager.show("GOOOO!!", 1.5f, Message.Type.Information, Position.Top, Size.Big);
	}

	@Override
	public void playerLapStarted () {
		// lapManager.stopRecording();
		playerCar.resetDistanceAndSpeed(true, false);
		lapManager.startRecording(playerCar, gameWorld.getLevelId(), userProfile.userId);
		progressData.reset(true);

		rebindAllReplays();
		restartAllReplays();
	}

	@Override
	public void playerLapCompleted () {
		// The policy is to permit slower replays at loading time, but not at gameplay time, so that a player will not be able to
		// save a slower replay. Slower replays may be loaded off disk just fine.
		if (lapManager.isRecording()) {
			lastRecorded.reset();

			Replay replay = lapManager.stopRecording();

			// check if better than current target
			GhostCar target = getNextTarget();
			boolean slowerThanTarget = (target != null) && (replay.compareTo(target.getReplay()) > -1);
			if (slowerThanTarget) {
				Replay treplay = target.getReplay();
				ReplayInfo ri = replay.getInfo();

				// early discard, slower than target
				lastRecorded.is_accepted = false;
				lastRecorded.discarded.copy(ri);
				lastRecorded.reason = DiscardReason.Slower;

				String diff = String.format("%.03f", (float)(ri.getMilliseconds() - treplay.getMilliseconds()) / 1000f);
				Gdx.app.log("ReplayManager", "Discarded replay #" + ri.getShortId() + " for " + diff + "secs");
			} else {
				// once added lastRecorded.new_replay should be used
				lastRecorded.copy(lapManager.addReplay(replay));
			}

			if (lastRecorded.is_accepted) {
				ReplayInfo ri = lastRecorded.accepted;

				CarUtils.dumpSpeedInfo("SinglePlayer", "Replay #" + ri.getShortId() + " accepted, player", playerCar, ri.getTicks());

				saveReplay(lastRecorded.new_replay);
				ReplayUtils.pruneReplay(lastRecorded.pruned); // prune if needed

				// show message
				int pos = lastRecorded.position;
				messager.show(pos + OrdinalUtils.getOrdinalFor(pos) + " place!", 1.5f, Message.Type.Information, Position.Top,
					Size.Big);
			} else {
				ReplayInfo ri = lastRecorded.discarded;

				String msg = "";
				String id = "(#" + ri.getShortId() + ")";
				float duration = 1.5f;

				switch (lastRecorded.reason) {
				case Null:
					msg = "Discarding null replay " + id;
					duration = 3;
					break;
				case InvalidMinDuration:
					msg = "Invalid lap (" + ri.getSecondsStr() + "s < " + GameplaySettings.ReplayMinDurationSecs + "s) " + id;
					duration = 10;
					break;
				case Invalid:
					msg = "The specified replay is not valid. (#" + ri.getShortId() + ") " + id;
					duration = 10;
					break;
				case WrongTrack:
					msg = "The specified replay belongs to another game track " + id;
					duration = 10;
					break;
				case Slower:
					msg = "Too slow!";
					duration = 1.5f;
					break;
				case NotDiscarded:
					break;
				}

				Gdx.app.log("SinglePlayer", msg);
				messager.show(msg, duration, Message.Type.Information, Position.Top, Size.Big);
			}

		}

		playerCar.resetDistanceAndSpeed(true, false);
	}

	@Override
	public void ghostLapCompleted (GhostCar ghost) {
		CarUtils.dumpSpeedInfo("SinglePlayer", "GhostCar #" + ghost.getId(), ghost, ghost.getReplay().getTicks());

		if (!hasPlayer()) {
			Replay last = lapManager.getReplays().peek();
			Replay ghostReplay = ghost.getReplay();

			if (last != null && last.getId().equals(ghostReplay.getId())) {
				restartAllReplays();
			}
		} else {
			// remove replay but do not reset its track state yet
			// ghost.removeReplay();
			ghost.stop();
		}
	}

	@Override
	public void ghostReplayStarted (GhostCar ghost) {
		if (ghost == findGhostFor(lapManager.getReplays().get(selectedBestReplayIdx))) {
			// ghost.setAlpha(Config.Graphics.DefaultTargetCarOpacity);
			playerTasks.hudPlayer.highlightNextTarget(ghost);
		}
	}

	@Override
	public void ghostReplayEnded (GhostCar ghost) {
		// can't stop the ghostcar here since it would stop the physics simulation for the GhostCar! Use the ghost lap completion
		// monitor instead!
		// ghost.stop();

		// CarUtils.dumpSpeedInfo("SinglePlayer", "GhostCar #" + ghost.getId(), ghost, ghost.getReplay().getTrackTime());

		// do the same as ghostfadingout
		if (ghost != null && ghost == getNextTarget()) {
			playerTasks.hudPlayer.unHighlightNextTarget();
		}
	}

	@Override
	public void doQuit () {
		lapManager.abortRecording(false);

		URacer.Screens.setScreen(ScreenType.MainScreen, TransitionType.Fader, 300);
		// URacer.Screens.setScreen(ScreenType.ExitScreen, TransitionType.Fader, 300);

		getTimeModulator().reset();
		URacer.Game.resetTimeModFactor();
	}

	//

	/** Restart all replays in the lap manager, if no next target set the best replay's car to it */
	private void restartAllReplays () {
		for (Replay r : lapManager.getReplays()) {
			if (hasGhostFor(r)) {
				GhostCar ghost = findGhostFor(r);
				boolean isnexttarget = (getNextTarget() == ghost);
				ghost.setAlpha(isnexttarget ? Config.Graphics.DefaultTargetCarOpacity : Config.Graphics.DefaultGhostCarOpacity);

				if (isnexttarget) {
					gameWorldRenderer.setTopMostGhostCar(ghost);
				}

				ghost.stop();
				ghost.start();
			}
		}
	}

	private void rebindAllReplays () {
		if (!(lapManager.getReplays().size <= ghostCars.length)) {
			throw new URacerRuntimeException("Replays count mismatch");
		}

		int g = 0;
		for (Replay r : lapManager.getReplays()) {
			GhostCar ghost = ghostCars[g];

			if (ghost == null) {
				throw new URacerRuntimeException("Ghost not ready (#" + g + ")");
			}

			ghost.setReplay(r);
			ghostLapMonitor[g].reset();

			g++;
		}

		// if no nextTarget then take the best (first)
		if (getNextTarget() == null) {
			Gdx.app.log("SinglePlayer", "Automatically selecting best replay...");
			if (lapManager.getReplaysCount() > 0) {
				selectedBestReplayIdx = 0;
				Gdx.app.log("SinglePlayer", "Done selecting best replay!");
			} else {
				selectedBestReplayIdx = -1;
				Gdx.app.log("SinglePlayer", "Couldn't find any replay for this track.");
			}
		}

	}
}
