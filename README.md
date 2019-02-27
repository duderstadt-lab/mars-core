#-------------------------------------------------------------------------------
# Copyright (C) 2019, Karl Duderstadt
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
# 
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# 3. Neither the name of the copyright holder nor the names of its contributors
#    may be used to endorse or promote products derived from this software
#    without specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
#-------------------------------------------------------------------------------
**MARS** - **M**olecule **AR**chive **S**uite - A collection of ImageJ2 commands for single-molecule analysis.

How to install this project in your local Fiji
===========================================
An imagej update site has been created to help with maintanance and distribution of MARS and all necessary dependencies. Follow the directions below to install MARS in your local copy of Fiji:
1. If you haven't already, download and install Fiji from https://imagej.net/Fiji/Downloads.
2. Open Fiji and make sure you are up-to-date by running Help>Update. Click accept changes to update to the newest versions of all components. After the update, restart Fiji.
3. Run Help>Update a second time, but now click Manage update sites. when click Add update site (DO NOT use Add my site, this is for Imagej Managed sites.). Once clicked, a new entry should appear. For Name put MARS and for URL put http://laue.biochem.mpg.de:83/ and then check the box to activte the update site. Now the main MARS jar file should show up as well as the necessary dependencies. Install them all and restart Fiji. If you don't immediately see the new jars restart the Updater.
4. If the plugins have been installed correctly, the submenu "MoleculeArchive Suite" should show up under Plugins.
5. From now on all you need to do is run the updater to ensure you have the current version of MARS installed. Please update frequently to ensure you benefit from the most recent bug fixes.

Documentation on how to use the commands and build groovy scripts with MARS can be found on the wiki at http://laue.biochem.mpg.de/Knowledge/Computing/Mars.

Copyright and license information
===========================================
Copyright (C) 2018 - 2019 Karl Duderstadt
  
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
 
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
 
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
