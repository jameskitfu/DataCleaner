/**
 * eobjects.org DataCleaner
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.datacleaner.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.inject.Inject;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.job.AnalysisJobMetadata;
import org.eobjects.analyzer.job.JaxbJobReader;
import org.eobjects.analyzer.job.NoSuchDatastoreException;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.datacleaner.bootstrap.WindowContext;
import org.eobjects.datacleaner.guice.DCModule;
import org.eobjects.datacleaner.user.UsageLogger;
import org.eobjects.datacleaner.user.UserPreferences;
import org.eobjects.datacleaner.util.FileFilters;
import org.eobjects.datacleaner.widgets.DCFileChooser;
import org.eobjects.datacleaner.widgets.OpenAnalysisJobFileChooserAccessory;
import org.eobjects.datacleaner.windows.AnalysisJobBuilderWindow;
import org.eobjects.datacleaner.windows.OpenAnalysisJobAsTemplateDialog;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

/**
 * ActionListener that will display an "Open file" dialog which allows the user
 * to select a job file.
 * 
 * The class also contains a few reusable static methods for opening job files
 * without showing the dialog.
 * 
 * @author Kasper Sørensen
 */
public class OpenAnalysisJobActionListener implements ActionListener {

	private final AnalyzerBeansConfiguration _configuration;
	private final AnalysisJobBuilderWindow _parentWindow;
	private final WindowContext _windowContext;
	private final DCModule _parentModule;
	private final UserPreferences _userPreferences;
	private final UsageLogger _usageLogger;

	@Inject
	protected OpenAnalysisJobActionListener(AnalysisJobBuilderWindow parentWindow, AnalyzerBeansConfiguration configuration,
			WindowContext windowContext, DCModule parentModule, UserPreferences userPreferences, UsageLogger usageLogger) {
		_parentWindow = parentWindow;
		_configuration = configuration;
		_windowContext = windowContext;
		_parentModule = parentModule;
		_userPreferences = userPreferences;
		_usageLogger = usageLogger;
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		_usageLogger.log("Open analysis job");

		DCFileChooser fileChooser = new DCFileChooser(_userPreferences.getAnalysisJobDirectory());

		OpenAnalysisJobFileChooserAccessory accessory = new OpenAnalysisJobFileChooserAccessory(_windowContext,
				_configuration, fileChooser, Providers.of(this));
		fileChooser.setAccessory(accessory);

		fileChooser.setFileFilter(FileFilters.ANALYSIS_XML);
		int openFileResult = fileChooser.showOpenDialog((Component) event.getSource());

		if (openFileResult == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			openFile(file);
		}
	}

	/**
	 * Opens a job file
	 * 
	 * @param file
	 */
	public void openFile(File file) {
		JaxbJobReader reader = new JaxbJobReader(_configuration);
		try {
			AnalysisJobBuilder ajb = reader.create(file);

			openJob(file, ajb);
		} catch (NoSuchDatastoreException e) {
			AnalysisJobMetadata metadata = reader.readMetadata(file);
			int result = JOptionPane.showConfirmDialog(null, e.getMessage()
					+ "\n\nDo you wish to open this job as a template?", "Error: " + e.getMessage(),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.ERROR_MESSAGE);
			if (result == JOptionPane.OK_OPTION) {
				OpenAnalysisJobAsTemplateDialog dialog = new OpenAnalysisJobAsTemplateDialog(_windowContext, _configuration,
						file, metadata, Providers.of(this));
				dialog.setVisible(true);
			}
		}
	}

	/**
	 * Opens a job builder
	 * 
	 * @param file
	 * @param ajb
	 */
	public void openJob(final File file, final AnalysisJobBuilder ajb) {
		_userPreferences.setAnalysisJobDirectory(file.getParentFile());
		_userPreferences.addRecentJobFile(file);

		Injector injector = Guice.createInjector(new DCModule(_parentModule, ajb) {
			public String getJobFilename() {
				return file.getName();
			};
		});

		AnalysisJobBuilderWindow window = injector.getInstance(AnalysisJobBuilderWindow.class);
		window.open();

		if (_parentWindow != null && !_parentWindow.isDatastoreSet()) {
			_parentWindow.close();
		}
	}
}
