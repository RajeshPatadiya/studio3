/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */

package com.aptana.ide.core.io;

import java.util.WeakHashMap;

import org.eclipse.core.internal.resources.DelayedSnapshotJob;
import org.eclipse.core.internal.resources.SaveManager;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ISavedState;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.aptana.core.logging.IdeLog;
import com.aptana.ide.core.io.auth.AuthenticationManager;
import com.aptana.ide.core.io.auth.IAuthenticationManager;
import com.aptana.ide.core.io.events.ConnectionPointEvent;
import com.aptana.ide.core.io.events.IConnectionPointListener;
import com.aptana.ide.core.io.internal.DeleteResourceShortcutListener;

/**
 * The activator class controls the plug-in life cycle
 */
@SuppressWarnings({"restriction", "deprecation"})
public class CoreIOPlugin extends Plugin
{

	// The plug-in ID
	public static final String PLUGIN_ID = "com.aptana.core.io"; //$NON-NLS-1$

	// The shared instance
	private static CoreIOPlugin plugin;

	private WeakHashMap<Object, ConnectionContext> connectionContexts = new WeakHashMap<Object, ConnectionContext>();

	private IConnectionPointListener listener = new IConnectionPointListener()
	{

		public void connectionPointChanged(ConnectionPointEvent event)
		{
			// saves the connections on any change instead of waiting for the
			// shutdown in case of workbench crash
			SaveManager saveManager = ((Workspace) ResourcesPlugin.getWorkspace()).getSaveManager();
			(new DelayedSnapshotJob(saveManager)).schedule();
		}
	};

	/**
	 * The constructor
	 */
	public CoreIOPlugin()
	{
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
		ISavedState lastState = ResourcesPlugin.getWorkspace().addSaveParticipant(this, new WorkspaceSaveParticipant());
		if (lastState != null)
		{
			IPath location = lastState.lookup(new Path(ConnectionPointManager.STATE_FILENAME));
			if (location != null)
			{
				ConnectionPointManager.getInstance().loadState(getStateLocation().append(location));
			}
		}

		ResourcesPlugin.getWorkspace().addResourceChangeListener(new DeleteResourceShortcutListener(),
				IResourceChangeEvent.POST_CHANGE);
		getConnectionPointManager().addConnectionPointListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception
	{
		try
		{
			ResourcesPlugin.getWorkspace().removeSaveParticipant(this);
			getConnectionPointManager().removeConnectionPointListener(listener);
			connectionContexts.clear();
		}
		finally
		{
			plugin = null;
			super.stop(context);
		}
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static CoreIOPlugin getDefault()
	{
		return plugin;
	}

	/**
	 * Returns the Connection Manager instance
	 * 
	 * @return
	 */
	public static IConnectionPointManager getConnectionPointManager()
	{
		return ConnectionPointManager.getInstance();
	}

	public static IAuthenticationManager getAuthenticationManager()
	{
		return AuthenticationManager.getInstance();
	}

	public static void setConnectionContext(Object key, ConnectionContext context)
	{
		getDefault().connectionContexts.put(key, context);
	}

	public static void clearConnectionContext(Object key)
	{
		getDefault().connectionContexts.remove(key);
	}

	public static ConnectionContext getConnectionContext(Object key)
	{
		return getDefault().connectionContexts.get(key);
	}

	private class WorkspaceSaveParticipant implements ISaveParticipant
	{

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.core.resources.ISaveParticipant#prepareToSave(org.eclipse.core.resources.ISaveContext)
		 */
		public void prepareToSave(ISaveContext context) throws CoreException
		{
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.core.resources.ISaveParticipant#saving(org.eclipse.core.resources.ISaveContext)
		 */
		public void saving(ISaveContext context) throws CoreException
		{
			IPath savePath = new Path(ConnectionPointManager.STATE_FILENAME).addFileExtension(Integer.toString(context
					.getSaveNumber()));
			ConnectionPointManager.getInstance().saveState(getStateLocation().append(savePath));
			context.map(new Path(ConnectionPointManager.STATE_FILENAME), savePath);
			context.needSaveNumber();
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.core.resources.ISaveParticipant#doneSaving(org.eclipse.core.resources.ISaveContext)
		 */
		public void doneSaving(ISaveContext context)
		{
			IPath prevSavePath = new Path(ConnectionPointManager.STATE_FILENAME).addFileExtension(Integer
					.toString(context.getPreviousSaveNumber()));
			getStateLocation().append(prevSavePath).toFile().delete();
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.core.resources.ISaveParticipant#rollback(org.eclipse.core.resources.ISaveContext)
		 */
		public void rollback(ISaveContext context)
		{
			IPath savePath = new Path(ConnectionPointManager.STATE_FILENAME).addFileExtension(Integer.toString(context
					.getSaveNumber()));
			getStateLocation().append(savePath).toFile().delete();
		}
	}

	/**
	 * Log a particular status
	 * 
	 * @deprecated Use IdeLog instead
	 */
	public static void log(IStatus status)
	{
		IdeLog.log(getDefault(), status);
	}
}
