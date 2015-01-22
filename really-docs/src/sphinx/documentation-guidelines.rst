.. _doc-guidelines:

########################
Documentation Guidelines
########################

Really's documentation is built using `Sphinx <http://sphinx-doc.org/>`__, which relies on the `reStructuredText markup language <http://docutils.sourceforge.net/rst.html>`__.

reStructuredText
================

Sphinx documentation provides the `reStructuredText Primer <http://sphinx-doc.org/rest.html>`__, a nice introduction to reST syntax and Sphinx *directives*.

Sections
--------

As reStructuredText allows any scheme for section headings (as long as it's consistent), the Really documentation use the following convention:

* ``#`` (over and under) for module headings
* ``=`` for sections
* ``-`` for subsections
* ``^`` for subsubsections
* ``~`` for subsubsubsections

Building the Documentation
==========================

You'll need to install Sphinx and some additional Python packages first.

Installing Sphinx
-----------------

Linux
^^^^^

* Install Python with your distribution's package manager
* If ``pip`` wasn't installed along Python, please `install it <http://pip.readthedocs.org/en/latest/installing.html>`__
* Install both Sphinx and PIL (`Python Imaging Library <http://www.pythonware.com/products/pil/>`__) using `pip`: ``pip install sphinx PIL``


Mac OS X
^^^^^^^^

* Install Python (`pip` is included) using `Homebrew <http://brew.sh/>`__ : ``brew install python``
* Install both Sphinx and PIL (`Python Imaging Library <http://www.pythonware.com/products/pil/>`__) using `pip`: ``pip install sphinx PIL``

For other platforms, please refer to `Sphinx installation guide <http://sphinx-doc.org/install.html>`__.

Running Sphinx
--------------

Really relies on the `sbt-site plugin <https://github.com/sbt/sbt-site>`__ to manage the Sphinx documentation build.

* ``sbt makeSite`` generates the Sphinx documentation in ``<project-dir>/target/sphinx/html/index.html``.
* ``sbt previewSite`` start a web server at ``localhost:4000`` which points to the documentation's index.


Working with the theme
----------------------

Documentation theme is `sphinx_rtd_theme <https://github.com/snide/sphinx_rtd_theme>`__, customized to match the Really design, you can find the customized theme at `really-docs-theme <https://github.com/reallylabs/really-docs-theme>`__

The theme is added to Really code as a subtree, so this will make it easy to separate the theme from Really code.
If there is a change to the theme design, it should be in `really-docs-theme <https://github.com/reallylabs/really-docs-theme>`__ not in this repo, then making s subtree pull here should get the new changes.

.. code:: bash

	$ git remote add really-docs-theme https://github.com/reallylabs/really-docs-theme

	$ git subtree pull --prefix=really-docs/src/sphinx/_themes/sphinx_rtd_theme really-docs-theme master