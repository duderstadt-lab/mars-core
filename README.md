[![](https://github.com/duderstadt-lab/mars-core/actions/workflows/build-main.yml/badge.svg)](https://github.com/duderstadt-lab/mars-core/actions/workflows/build-main.yml)

<p><img src="https://raw.githubusercontent.com/duderstadt-lab/mars-docs/master/assets/MARS%20front%20page.png" width=“800"></p>

**Mars** - **M**olecule **AR**chive **S**uite - A framework for storage and reproducible processing of single-molecule datasets.

How to install this project in your local Fiji
===========================================
An imagej update site has been created to help with maintanance and distribution of MARS and all necessary dependencies. Follow the directions below to install Mars in your local copy of Fiji:
1. If you haven't already, download and install Fiji from https://imagej.net/Fiji/Downloads.
2. Open Fiji and make sure you are up-to-date by running Help>Update. Click accept changes to update to the newest versions of all components. After the update, restart Fiji.
3. Run Help>Update a second time, but now click Manage update sites. Then click Add update site to create a new entry. For name put Mars and for URL put http://sites.imagej.net/Mars/ and then check the box to activate the update site. Now mars-core, mars-fx, mars-scifio, mars-trackmate, mars-fmt (the gui for MarsTables and Molecule Archives) files should show up as well as the necessary dependencies. Install them all and restart Fiji. If you don't see the new jars restart the Updater.
4. If the plugins have been installed correctly, the submenu "MoleculeArchive Suite" should show up under Plugins.
5. From now on all you need to do is run the updater to ensure you have the current version of Mars installed. Please update frequently to ensure you benefit from the most recent bug fixes.

Mars documentation can be found at https://duderstadt-lab.github.io/mars-docs/

