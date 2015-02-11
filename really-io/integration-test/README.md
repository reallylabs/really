Integration Test
======

```
How to run integration test
```

## Prepare Test Enviroment

   run install_env.sh
   
    ./install_env.sh
    
   this script will install
   
      - python
      - pip
      - vitualenv
  
### 1- Create Python Virtual Enviroment

    ./init_env.sh

### 2- Boot Servers
   before run integration test we need to run below servers
   
   1- Really Server
   
    activator "project really-io" run
   
   2- Authentication Server
   
    activator "project really-simple-auth" "run 8080"
    
### 3- Run test cases
   1- source test enviroment
   
    source environment/bin/activate

   2- run all testcases

    nosetests
