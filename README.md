# Cider-CI Server
=======

Part of [Cider-CI](https://github.com/cider-ci/cider-ci).

## License

Copyright (C) 2013 - 2017 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
Licensed under the terms of the GNU Affero General Public License v3.
See the "LICENSE.txt" file provided with this software.


## Usage

### Database Migrations

    lein run -- server migrate -d "jdbc:postgresql://cider-ci:cider-ci@localhost:/cider-ci_v4"


## DEV Notes 

### Import test project

1. create project 'test', create api KEY

2. `git push http://V4GP5JJPKVXNMUAHILK25J3AF4DCPZJW@localhost:8881/projects/test.git HEAD:test`

3. import branches `(some-> @projects* (get "test") git-sql/import-branches)` in `cider-ci.server.projects` 


## GPG NOTES

How to manually verify a signed git commit with GPG. This assumes you have GPG installed, and you have the public key added to the default keyring.

https://gist.github.com/toolmantim/3e835c5e34bdceed0425a0d8f350338d

TODO:

1. reproduce it
2. validate signature with bouncy castle



git cat-file -p 18ea9854f

tree 236c681d0e2c3fdeb920c8f530358e3f6695ca11
parent b989a8075f29a8407ae6d7a43c03c06e3f595296
author Thomas Schank <DrTom@schank.ch> 1520337840 +0100
committer Thomas Schank <DrTom@schank.ch> 1520421437 +0100
gpgsig -----BEGIN PGP SIGNATURE-----
 Comment: GPGTools - http://gpgtools.org

 iQJEBAABCAAuFiEE0YC+ieafY5lxHRWQ4pNFnLELBEIFAlqfyj0QHGRydG9tQHNj
 aGFuay5jaAAKCRDik0WcsQsEQjxED/9WiVmtprys1/QKv901f1vNcJW/o8+RE51w
 B5d8xMyfG3rzlzZzv1ckWRUb7eSwoPPS2VeCaqqsdtleO1QhdZg4OKsGrq9dRAJP
 T4VvnWbBImYMcVZe3mySnEjfTCy7kB6d9VjyxT1oLD1yXJTJYOKN8+YvGh96uaNX
 tt1LG6MEnqoDdQWCMZiYhatFnUvFURCO9Wj67pLC5mCNPMbe9SiU59CJ8l20kGH7
 0Kuyb6DEO4hOHCmB137+Gr2M5R1vqKRDoIB7q5MpvvgMj3zOfvmZuNq5tbbt/vaF
 JIQ4H/TXLi8PTn/5TOaRVbHjhNHZGkP5KaX2ZrQ7PAWo3BJzz4oHNAj7Bv+uG58S
 PTPC8pydw2yK5A0VrwJiWmooD8RDaJC83TLs7mAU7gf2um9vle2RiU9CdZZmWN9j
 MJZSJevdn1DC2p2CsTqqxVNvUlJwBuEaRhiZe+EYaKi3rVWHVF214LR2BT5dVBIP
 8Cw94bXMdEf02MmUaO76xXNSW4DW+ZSIGfCsh6NfppDzF0Aw6aOxaPHWTW2aCFAj
 VzDdYLmsTxo/Q+x9nrp1mjwe+SBUnTyZ403uFwiJ/mVWYHPZEpI+KbzoWtDa/9/y
 M9xcj9DkzMiPDQQ/yOzJm9kADyhNM6N0J+Rx0Wf10Rw5IV5IOP07Z5iHUnBttq+/
 SlZBwBAihQ==
 =ktXx
 -----END PGP SIGNATURE-----

Sqaushed v5 commits



git tag -s 'sign-test' -m 'message'

git show sign-test

    tag sign-test
    Tagger: Thomas Schank <DrTom@schank.ch>
    Date:   Thu Mar 8 19:49:14 2018 +0100

    message
    -----BEGIN PGP SIGNATURE-----
    Comment: GPGTools - http://gpgtools.org

    iQJEBAABCAAuFiEE0YC+ieafY5lxHRWQ4pNFnLELBEIFAlqhhaoQHGRydG9tQHNj
    aGFuay5jaAAKCRDik0WcsQsEQpkqD/9fbINLSrJed1GUlcVX9IOMCj9ypMC9XCVp
    TV/6CTlB7PZnxZpBEr41CsfOs11qBe6sL5VQeE8T50dOBUPzzERFEc8rdQT0lRr6
    p4IL3ikiWn4bZj5UhGB3zz3bZP5P1hsR+Gu58YDF6zbIkDBtirOHQWwR2yEvOwmH
    hop1/mWrtu4hd0aAJyTn6NpY62k8WAAXQju6k00xP26v4coE0D3rs2q6VboiIKbm
    WUJa+FS9DPQlQ7C+kk7bu76vc7Ued5V+EIZG1CRbSJXDTs8GDfLZJzTiPRIPRcrX
    p2bAvfZMd+dRrFeS4DJ1cNqdpZbvSd3LLwkrz6iWQrIvMhbYplzYzz/nBunTzaBP
    9/EsrBDGtcbnNdbCVJiuylM03cRfwTeLxmIGpwL1KzNT3/E4843gQDxjbe4z8kGJ
    uVNYue6eeU3Nv012voi+ceOJfXoIw07eT3iLoVBMICZWPUcmITDARBR9IIKAqq6W
    gbEhTfZIZH/9wCC8qP3pT7k+zhf1NP5s01mpHs2sen25bFyEAI8KWh3eLdHCp/MS
    eOKCqV2J9c4Q8QSfVAYjR8t5y1glXBTu/Dt6Op2ZKkmZJl7snxhDKTXiez3xnCFK
    I76vBCjv2qJjYcJgbSQhYKEW2heU8jidWAGmCsyqyVzxHBqNU6D8cgwd3/u6Gw2g
    ucOaUsX/4Q==
    =yd4C
    -----END PGP SIGNATURE-----
