 #!/bin/bash

# Copyright (C) 2014-2015 Really Inc. <http://really.io>

cp -rf models ../../.really/

virtualenv --distribute environment
export PYTHONPATH=
source environment/bin/activate
pip install -r requirements.pip
