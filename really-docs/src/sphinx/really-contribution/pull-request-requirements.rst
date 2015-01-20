Pull Request Requirements
=========================

For a Pull Request to be considered at all it has to meet these requirements:

1. Live up to the current code standard:

   * Not violate `DRY <http://programmer.97things.oreilly.com/wiki/index.php/Don%27t_Repeat_Yourself>`_.
   * `Boy Scout <http://programmer.97things.oreilly.com/wiki/index.php/The_Boy_Scout_Rule>`_ Rule needs to have been applied.

2. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.
3. The code must be well documented in the really's standard documentation format.
4. The commit messages must properly describe the changes, `see further below <www.todo.dom>`_.
5. The pull request title should contain the task number in JIRA
6. All source files in the project must have a really copyright notice in the file header.

Other guidelines to follow for copyright notices:

	    * Use a form of Copyright (C) 2014-2015 really Inc. <http://www.really.io>, where the start year is when the project or file was first created and the end year is the last time the project or file was modified.
	    * Never delete or change existing copyright notices, just add additional info.
	    * Do not use @author tags since it does not encourage `Collective Code Ownership <http://www.extremeprogramming.org/rules/collective.html>`_. However, each project should make sure that the contributors gets the credit they deserve—in a text file or page on the project website and in the release notes etc.

::
	
   /**
    * Copyright (C) 2014-2015 Really Inc. <http://really.io>
    */

If these requirements are not met then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions.

Whether or not a pull request (or parts of it) shall be back- or forward-ported will be discussed on the pull request discussion page, it shall therefore not be part of the commit messages. If desired the intent can be expressed in the pull request description.