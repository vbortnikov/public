// Findning login names in Jenkins credentials by HTTPBuilder framework
// https://github.com/jgritman/httpbuilder/wiki

// Working without global network
@GrabResolver(name='my_nexus',root='http://my_nexus.ru/nexus/content/groups/public')
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2')

// Parameters from Jenkins
// and got the bug: this declaration must start with def-style variable declaration
def baseUrl = System.getProperty('baseUrl')
def userName = System.getProperty('userName')
def password = System.getProperty('password')
userNameRegex = System.getProperty('userNameRegex') // without def, it's global scope
verbose = System.getProperty('verbose').toBoolean()

found = ""  // String with search result in global scope
                
def find_creds(configUrl)  {
// Searching Folder config for credentials
    try  {  
      jenkins.get( uri : configUrl + '/config.xml' ) { resp, xml ->
		  // Working with GPath 
          def creds = xml.properties.'com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider_-FolderCredentialsProperty'.domainCredentialsMap.entry.'java.util.concurrent.CopyOnWriteArrayList'.'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl' 
          creds.each()  {
            if (verbose)  println it.username
            if (it.username.toString().toLowerCase() ==~ "${userNameRegex}") {
              if (verbose)  { println "found:" + it.username  }
              found += configUrl + '/credentials/  ' + it.username + '\n'
            }   
          }
      }

    }  catch (groovyx.net.http.HttpResponseException ex)  {
      if (ex.statusCode == 403)  {
        if (verbose) println "GOT 403 (forbidden) error!"
      }  else {
               println "Caught exception: ${ex}"
      }  
    }  
}    

def scan_creds(url)  {
// Recursion through Jenkins tree

  if (verbose) println url
  find_creds(url)

  // have to build uri in such way because of encoded (Russian with backspaces) URL
  def uri = new groovyx.net.http.URIBuilder( new URI( url + '/api/json' )  )
    jenkins.get( uri : uri ) { resp, json ->
        json.jobs.each { job ->
            if (job._class == 'com.cloudbees.hudson.plugins.folder.Folder')  {
                scan_creds(job.url) //recursion call
            }
        }
    }
}

jenkins = new groovyx.net.http.HTTPBuilder(baseUrl)
//jenkins.ignoreSSLIssues() // turn it on if you don't want to check SSL certificate

def authString = "${userName}:${password}".getBytes().encodeBase64().toString();
// Have to use setHeaders() in order to Pre-emptive authentication works
jenkins.setHeaders([Authorization: "Basic ${authString}"])

// Works here
scan_creds(baseUrl)

//Output results
if (found.length())  {
  println "\n\nSearch results:"
  println found + "\n"
} else   println "\n\nSorry, not found.\n\n"
