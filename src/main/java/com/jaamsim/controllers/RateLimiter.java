/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2014 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.jaamsim.controllers;

import java.util.ArrayList;

/**
 * A class to limit updates to a set rate in real time
 * @author matt.chudleigh
 *
 */
public class RateLimiter implements Runnable {
	private final Thread refreshThread;

	private long lastCallbackTime = 0;
	private long scheduledTime = Long.MAX_VALUE;
	private final Object timingLock = new Object();
	private final double ups;
	private boolean running = true;

	private final ArrayList<Runnable> callbacks = new ArrayList<Runnable>();

	public static RateLimiter create(double updatesPerSecond) {
		RateLimiter ret = new RateLimiter(updatesPerSecond);
		ret.refreshThread.start();
		return ret;
	}

	private RateLimiter(double updatesPerSecond) {
		// Start the display timer
		ups = updatesPerSecond;

		refreshThread = new Thread(this, "RefreshThread");
		refreshThread.setDaemon(true);
	}

	@Override
	public void run() {
		synchronized(timingLock) {
			while(running) {
				// Is a redraw scheduled
				long currentTime = System.currentTimeMillis();
				if (scheduledTime > currentTime) {
					try {
						// We have a scheduled time, wait until then
						timingLock.wait(scheduledTime - currentTime);
					}
					catch(InterruptedException ex) {}
					continue;
				}

				lastCallbackTime = currentTime;
				scheduledTime = Long.MAX_VALUE;
				for (Runnable r : callbacks) {
					r.run();
				}
			}
		}
	}

	public void queueUpdate() {
		synchronized(timingLock) {
			// If we already are schedule to run, don't wake the thread
			if (scheduledTime != Long.MAX_VALUE)
				return;

			// Set a new target callback time based on the last time a callback was
			// actually done
			long frameTime = (long)(1000.0d / ups);
			scheduledTime = lastCallbackTime + frameTime;
			timingLock.notify();
		}
	}

	public void registerCallback(Runnable r) {
		synchronized (timingLock) {
			callbacks.add(r);
		}
	}

	public void cancel() {
		synchronized (timingLock) {
			running = false;
		}
	}
}