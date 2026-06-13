#
# Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
# Copyright (C) 2026 - Jose Moya
#
# This file is hereby licensed under the terms of the GNU GPL v2.0,
# pursuant to article 5.3.4 of the CeCILL v.2.1.
# This file was originally licensed under the terms of the CeCILL v2.1,
# and continues to be available under such terms.
# For more information, see the COPYING file which you should have received
# along with this program.
#

#### Target ######
modulename=terminal

# The terminal module is Java + macros only (JediTerm + a JNA PTY); it has no
# native C gateway. terminal() is dispatched from a macro via jimport.

#### terminal : Conf files ####
libsciterminal_la_rootdir = $(mydatadir)
libsciterminal_la_root_DATA = license.txt


#### terminal : init scripts ####
libsciterminal_la_etcdir = $(mydatadir)/etc
libsciterminal_la_etc_DATA = etc/terminal.quit \
				etc/terminal.start

include $(top_srcdir)/Makefile.incl.am

if GUI
USEANT=1
endif
