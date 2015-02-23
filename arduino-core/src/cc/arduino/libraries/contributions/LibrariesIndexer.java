/*
 * This file is part of Arduino.
 *
 * Copyright 2014 Arduino LLC (http://www.arduino.cc/)
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 */
package cc.arduino.libraries.contributions;

import static processing.app.I18n._;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import processing.app.BaseNoGui;
import processing.app.I18n;
import processing.app.helpers.FileUtils;
import processing.app.helpers.filefilters.OnlyDirs;
import processing.app.packages.LegacyUserLibrary;
import processing.app.packages.LibraryList;
import processing.app.packages.UserLibrary;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;

public class LibrariesIndexer {

  private LibrariesIndex index;
  private LibraryList installedLibraries = new LibraryList();
  private List<File> librariesFolders;
  private File indexFile;
  private File stagingFolder;
  private File sketchbookLibrariesFolder;

  public LibrariesIndexer(File preferencesFolder) {
    indexFile = new File(preferencesFolder, "library_index.json");
    stagingFolder = new File(preferencesFolder, "staging" + File.separator +
        "libraries");
  }

  public void parseIndex() throws JsonParseException, IOException {
    parseIndex(indexFile);

    index.fillCategories();
    // TODO: resolve libraries inner references
  }

  private void parseIndex(File indexFile) throws JsonParseException,
      IOException {
    InputStream indexIn = new FileInputStream(indexFile);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new MrBeanModule());
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    mapper.configure(DeserializationFeature.EAGER_DESERIALIZER_FETCH, true);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    index = mapper.readValue(indexIn, LibrariesIndex.class);
  }

  public void setLibrariesFolders(List<File> _librariesFolders)
      throws IOException {
    librariesFolders = _librariesFolders;
    rescanLibraries();
  }

  public void rescanLibraries() throws IOException {
    // Clear all installed flags
    installedLibraries.clear();
    for (ContributedLibrary lib : index.getLibraries())
      lib.setInstalled(false);

    // Rescan libraries
    for (File folder : librariesFolders)
      scanInstalledLibraries(folder);
  }

  private void scanInstalledLibraries(File folder) {
    File list[] = folder.listFiles(OnlyDirs.ONLY_DIRS);
    // if a bad folder or something like that, this might come back null
    if (list == null)
      return;

    for (File subfolder : list) {
      if (!BaseNoGui.isSanitaryName(subfolder.getName())) {
        String mess = I18n.format(_("The library \"{0}\" cannot be used.\n"
            + "Library names must contain only basic letters and numbers.\n"
            + "(ASCII only and no spaces, and it cannot start with a number)"),
                                  subfolder.getName());
        BaseNoGui.showMessage(_("Ignoring bad library name"), mess);
        continue;
      }

      try {
        scanLibrary(subfolder);
      } catch (IOException e) {
        System.out.println(I18n.format(_("Invalid library found in {0}: {1}"),
                                       subfolder, e.getMessage()));
      }
    }
  }

  private void scanLibrary(File folder) throws IOException {
    boolean readOnly = !FileUtils
        .isSubDirectory(sketchbookLibrariesFolder, folder);

    // A library is considered "legacy" if it doesn't contains
    // a file called "library.properties"
    File check = new File(folder, "library.properties");
    if (!check.exists() || !check.isFile()) {

      // Create a legacy library and exit
      LegacyUserLibrary lib = LegacyUserLibrary.create(folder);
      lib.setReadOnly(readOnly);
      installedLibraries.addOrReplace(lib);
      return;
    }

    // Create a regular library
    UserLibrary lib = UserLibrary.create(folder);
    lib.setReadOnly(readOnly);
    installedLibraries.addOrReplace(lib);

    // Check if we can find the same library in the index
    // and mark it as installed
    String libName = folder.getName(); // XXX: lib.getName()?
    ContributedLibrary foundLib = index.find(libName, lib.getVersion());
    if (foundLib != null) {
      foundLib.setInstalled(true);
      foundLib.setInstalledFolder(folder);
      foundLib.setReadOnly(readOnly);
    }
  }

  public LibrariesIndex getIndex() {
    return index;
  }

  public LibraryList getInstalledLibraries() {
    return installedLibraries;
  }

  public File getStagingFolder() {
    return stagingFolder;
  }

  /**
   * Set the sketchbook library folder. <br />
   * New libraries will be installed here. <br />
   * Libraries not found on this folder will be marked as read-only.
   * 
   * @param folder
   */
  public void setSketchbookLibrariesFolder(File folder) {
    this.sketchbookLibrariesFolder = folder;
  }

  public File getSketchbookLibrariesFolder() {
    return sketchbookLibrariesFolder;
  }

  public File getIndexFile() {
    return indexFile;
  }
}
