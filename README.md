[![](https://travis-ci.com/duderstadt-lab/mars-core.svg?branch=master)](https://travis-ci.com/duderstadt-lab/mars-core)

<p><img src="https://raw.githubusercontent.com/duderstadt-lab/mars-docs/master/assets/MARS%20front%20page.png" width=“800"></p>

**Mars** - **M**olecule **AR**chive **S**uite - A framework for storage and reproducible processing of single-molecule datasets.

How to install this project in your local Fiji
===========================================
An imagej update site has been created to help with maintanance and distribution of MARS and all necessary dependencies. Follow the directions below to install MARS in your local copy of Fiji:
1. If you haven't already, download and install Fiji from https://imagej.net/Fiji/Downloads.
2. Open Fiji and make sure you are up-to-date by running Help>Update. Click accept changes to update to the newest versions of all components. After the update, restart Fiji.
3. Run Help>Update a second time, but now click Manage update sites. Then click Add update site to create a new entry. For Name put MARS and for URL put http://sites.imagej.net/Mars/ and then check the box to activate the update site. Now mars-core and mars-swing (the gui for Tables, Plots and MoleculeArchives) files should show up as well as the necessary dependencies. Install them all and restart Fiji. If you don't see the new jars restart the Updater.
4. If the plugins have been installed correctly, the submenu "MoleculeArchive Suite" should show up under Plugins.
5. From now on all you need to do is run the updater to ensure you have the current version of MARS installed. Please update frequently to ensure you benefit from the most recent bug fixes.

Mars documentation can be found at https://duderstadt-lab.github.io/mars-docs/

Copyright and license information
===========================================
Copyright (C) 2019, Duderstadt Lab
All rights reserved.
 
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 
1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
 
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
 
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
