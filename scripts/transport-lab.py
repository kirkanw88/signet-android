"""Isolated loopback Xray 26.11.9 lab. No production credentials or servers.

python scripts/transport-lab.py --serve (keeps fixtures running for Android tests)
python scripts/transport-lab.py (host e2e checks; cleans up owned processes)
Private test keys/configs stay under ignored .gradle/transport-lab.
"""
import argparse, contextlib, hashlib, http.server, ipaddress, json, os, pathlib
import shutil, socket, socketserver, ssl, struct, subprocess, threading, time, urllib.parse, urllib.request, zipfile, re, sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
LAB=ROOT/'.gradle/transport-lab';LAB.mkdir(parents=True,exist_ok=True)
UUID='00000000-0000-4000-8000-000000000001'
PRIVATE='4BQvwZVnV6UTS8QsF_JT-9e8pmLW5cXAQTe7Jj9z4Us'
PUBLIC='6fJ8ZrtKPl03dmX4FA6WG61MIGgCm_sJKfZ0G4onWw0'
CREATION={'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {}
owned=[];servers=[];outputs=[]

def core():
    windows=os.name=='nt';name='Xray-windows-64.zip' if windows else 'Xray-linux-64.zip'
    expected='1262522266337b824f07beb8e1375786b437683b3890105469088f8e17e0a69d' if windows else 'b651f7e1d9966ee2c1b1ee1bf8b52c72771824d3065dce96bbc8bcc19ed95f82'
    archive=LAB/name
    if not archive.exists():
        with urllib.request.urlopen('https://github.com/XTLS/Xray-core/releases/download/v26.11.9/'+name,timeout=120) as r,archive.open('wb') as f:
            while block:=r.read(1024*1024):f.write(block)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise RuntimeError('Official host-core archive checksum mismatch')
    binary=LAB/('xray.exe' if windows else 'xray')
    with zipfile.ZipFile(archive) as z:
        for name in (binary.name,"geoip.dat","geosite.dat"):
            expected_bytes=z.read(name);target=LAB/name
            if not target.exists() or hashlib.sha256(target.read_bytes()).digest()!=hashlib.sha256(expected_bytes).digest():target.write_bytes(expected_bytes)
    if not windows:binary.chmod(0o700)
    return str(binary)

def cmd(args):return subprocess.run(args,check=True,capture_output=True,**CREATION)

def certificates():
    openssl=shutil.which('openssl') or ('C:/Program Files/Git/usr/bin/openssl.exe' if os.name=='nt' else 'openssl')
    if not (LAB/'server.pem').exists():
        cmd([openssl,'req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(LAB/'ca.key'),'-out',str(LAB/'ca.pem'),'-days','2','-subj','/CN=Signet Local Test CA','-addext','basicConstraints=critical,CA:TRUE'])
        cmd([openssl,'req','-newkey','rsa:2048','-nodes','-keyout',str(LAB/'server.key'),'-out',str(LAB/'server.csr'),'-subj','/CN=localhost'])
        (LAB/'server.ext').write_text('subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1,IP:198.18.0.2,IP:fd17:ffff::2\nextendedKeyUsage=serverAuth\n')
        cmd([openssl,'x509','-req','-in',str(LAB/'server.csr'),'-CA',str(LAB/'ca.pem'),'-CAkey',str(LAB/'ca.key'),'-CAcreateserial','-out',str(LAB/'server.pem'),'-days','2','-sha256','-extfile',str(LAB/'server.ext')])
    return (LAB/'ca.pem').read_text().splitlines()

objects={};store_lock=threading.Lock();requests=0;fail_store=False
class Store(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def end(self,code,body=b''):
        self.send_response(code);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def dispatch(self):
        global requests
        with store_lock:requests+=1
        key=urllib.parse.unquote(urllib.parse.urlsplit(self.path).path.removeprefix('/folder/'))
        if self.path=='/__stats':return self.end(200,json.dumps({'requests':requests,'objects':len(objects)}).encode())
        if fail_store:return self.end(503)
        if self.command=='PROPFIND':
            with store_lock:body='\n'.join('<d:href>/folder/'+k+'</d:href>' for k in objects).encode()
            return self.end(200,body)
        if self.command=='PUT':
            count=int(self.headers.get('Content-Length','0'))
            if count>1024*1024:return self.end(413)
            body=self.rfile.read(count)
            with store_lock:
                if sum(map(len,objects.values()))>16*1024*1024:return self.end(507)
                objects[key]=body
            return self.end(201)
        if self.command=='GET':
            with store_lock:body=objects.get(key)
            return self.end(404 if body is None else 200,body or b'')
        if self.command=='DELETE':
            with store_lock:objects.pop(key,None)
            return self.end(204)
    do_GET=do_PUT=do_DELETE=do_PROPFIND=dispatch

class Echo(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        body=b'signet-local-lab';self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)

class LocalHttpServer(http.server.ThreadingHTTPServer):
    def handle_error(self,*args):
        if isinstance(sys.exc_info()[1],(ConnectionResetError,BrokenPipeError,ssl.SSLError,TimeoutError)):return
        super().handle_error(*args)

class LocalTlsServer(LocalHttpServer):
    def finish_request(self,request,address):
        request.settimeout(6)
        with self.tls_context.wrap_socket(request,server_side=True) as secure:
            super().finish_request(secure,address)

class Dns(socketserver.BaseRequestHandler):
    def handle(self):
        raw,sock=self.request
        if len(raw)<12:return
        answer=raw[:2]+b'\x81\x80\x00\x01\x00\x01\x00\x00\x00\x00'+raw[12:]+b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x1e\x00\x04'+socket.inet_aton('198.18.0.1')
        sock.sendto(answer,self.client_address)

def start_services():
    for port,handler in ((19095,Store),(18480,Echo),(18443,Echo)):
        server=(LocalTlsServer if port==18443 else LocalHttpServer)(('127.0.0.1',port),handler)
        if port==18443:
            context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(str(LAB/'server.pem'),str(LAB/'server.key'));server.tls_context=context
        servers.append(server);threading.Thread(target=server.serve_forever,daemon=True).start()
    server=socketserver.ThreadingUDPServer(('127.0.0.1',18553),Dns);servers.append(server);threading.Thread(target=server.serve_forever,daemon=True).start()

def template():
    base='http://127.0.0.1:19095/folder/'
    return {'flatten':True,'put':{'method':'PUT','url':base+'{name}'},'get':{'method':'GET','url':base+'{name}'},'delete':{'method':'DELETE','url':base+'{name}'},'list':{'method':'PROPFIND','url':base+'?prefix={prefix}','namesRegex':'<d:href>/folder/([^<]+)</d:href>'},'retry':{'status':[429,500,502,503]}}

def xdrive():return {'network':'xdrive','xdriveSettings':{'service':'template','remoteFolder':'folder','template':template(),'flushIntervalMs':10,'pollIntervalMs':20,'maxPollIntervalMs':200,'segmentBytes':32768,'concurrency':2}}
def tls(root,server=False,alpn='h2'):
    return {'alpn':[alpn],**({'certificates':[{'certificateFile':str(LAB/'server.pem'),'keyFile':str(LAB/'server.key')}]} if server else {'serverName':'localhost','allowInsecure':False,'certificates':[{'certificate':root,'usage':'verify'}]})}
def vless_client(port,stream):return {'tag':'proxy','protocol':'vless','settings':{'vnext':[{'address':'127.0.0.1','port':port,'users':[{'id':UUID,'encryption':'none'}]}]},'streamSettings':stream}
def vless_server(port,stream,vision=False):return {'listen':'127.0.0.1','port':port,'protocol':'vless','settings':{'clients':[{'id':UUID,**({'flow':'xtls-rprx-vision'} if vision else {})}],'decryption':'none'},'streamSettings':stream}

def generate(root,binary):
    material=cmd([binary,"vlessenc"]).stdout.decode()
    decryption=re.findall(r'"decryption": "([^"\n]+)"',material)[0]
    encryption=re.findall(r'"encryption": "([^"\n]+)"',material)[0]
    inbound=[vless_server(10888,{'network':'tcp','security':'none'})]
    reality={'dest':'www.microsoft.com:443','serverNames':['www.microsoft.com'],'privateKey':PRIVATE,'shortIds':['0123456789abcdef']}
    inbound+=[vless_server(10889,{'network':'tcp','security':'reality','realitySettings':reality},True),vless_server(10890,{'network':'xhttp','security':'reality','realitySettings':reality,'xhttpSettings':{'path':'/signet-local'}}),vless_server(10894,xdrive())]
    inbound[-1]["settings"]["decryption"]=decryption
    for port,alpn in ((10891,'h2'),(10892,'h3')):
        inbound.append({'listen':'127.0.0.1','port':port,'protocol':'masque','settings':{'users':[{'email':'lab@example.invalid','pass':'lab-password'}],'address':['10.17.0.1/24','fd17:abcd::1/64'],'mtu':1400},'streamSettings':{'network':'masque','security':'tls','tlsSettings':tls(root,True,alpn),'masqueSettings':{'path':'/signet'}}})
    redirects=[('198.18.0.1/32','127.0.0.1:18480'),('198.18.0.2/32','127.0.0.1:18443'),('198.18.0.3/32','127.0.0.1:18553')]
    rules=[];outbound=[{'tag':'direct','protocol':'freedom'}]
    for i,(ip,dest) in enumerate(redirects):
        tag='lab-'+str(i);rules.append({'type':'field','ip':[ip],'outboundTag':tag});outbound.append({'tag':tag,'protocol':'freedom','settings':{'redirect':dest,'finalRules':[{'action':'allow','network':'udp' if dest.endswith(':18553') else 'tcp','ip':['127.0.0.1/32'],'port':dest.rsplit(':',1)[1]}]}})
    server={'log':{'loglevel':'warning'},'dns':{'hosts':{'direct.signet.test':'127.0.0.1','google.com':'198.18.0.1'},'servers':[{'address':'127.0.0.1','port':18553,'domains':['domain:signet.test'],'skipFallback':True},'1.1.1.1']},'inbounds':inbound,'outbounds':outbound,'routing':{'domainStrategy':'IPIfNonMatch','rules':rules}}
    profiles={}
    profiles['vless']=vless_client(10888,{'network':'tcp','security':'none'})
    r={'serverName':'www.microsoft.com','fingerprint':'chrome','publicKey':PUBLIC,'shortId':'0123456789abcdef'}
    profiles['reality']=vless_client(10889,{'network':'tcp','security':'reality','realitySettings':r});profiles['reality']['settings']['vnext'][0]['users'][0]['flow']='xtls-rprx-vision'
    profiles['xhttp']=vless_client(10890,{'network':'xhttp','security':'reality','realitySettings':r,'xhttpSettings':{'path':'/signet-local','mode':'packet-up'}})
    profiles['xdrive']=vless_client(10894,xdrive())
    profiles['xdrive']['settings']['vnext'][0]['users'][0]['encryption']=encryption
    profiles['xdrive']['mux']={'enabled':True,'concurrency':8,'xudpConcurrency':8,'xudpProxyUDP443':'reject'}
    for name,port,alpn in (('masque-h2',10891,'h2'),('masque-h3',10892,'h3')):
        profiles[name]={'tag':'proxy','protocol':'masque','settings':{'address':'127.0.0.1','port':port,'remoteDNS':['1.1.1.1']},'streamSettings':{'network':'masque','security':'tls','tlsSettings':tls(root,False,alpn),'masqueSettings':{'path':'/signet','user':'lab@example.invalid','pass':'lab-password'}}}
    configs={name:{'remarks':'LOCAL TEST ONLY','outbounds':[out]} for name,out in profiles.items()}
    auto=json.loads(json.dumps(configs['vless']));auto['remarks']='AUTO';auto['inbounds']=[{'tag':'socks-in','protocol':'socks','port':0}]
    auto['outbounds']=[{**profiles['vless'],'tag':'proxy-a'},{**profiles['vless'],'tag':'proxy-b'},{'tag':'block','protocol':'blackhole'}]
    auto['routing']={'domainStrategy':'AsIs','balancers':[{'tag':'auto','selector':['proxy-'],'strategy':{'type':'random'}}],'rules':[{'type':'field','domain':['full:blocked.signet.test'],'outboundTag':'block'},{'type':'field','network':'tcp,udp','balancerTag':'auto'}]}
    configs['remnawave-auto']=auto
    split=json.loads(json.dumps(configs['vless']));split['remarks']='Remnawave split template'
    split['dns']={'hosts':{'direct.signet.test':'127.0.0.1'},'servers':['1.1.1.1']}
    split['outbounds'] += [{'tag':'direct','protocol':'freedom','settings':{'domainStrategy':'UseIP'}},{'tag':'block','protocol':'blackhole'}]
    split['routing']={'domainStrategy':'IPOnDemand','rules':[
        {'type':'field','domain':['full:blocked.signet.test','geosite:google'],'outboundTag':'block'},
        {'type':'field','ip':['198.18.0.0/16'],'outboundTag':'proxy'},
        {'type':'field','ip':['geoip:private'],'port':'18480','outboundTag':'direct'},
        {'type':'field','network':'tcp,udp','outboundTag':'proxy'}]}
    configs['remnawave-split']=split
    # Official default-template contract after the generator prepends a host outbound.
    default=json.loads((ROOT/'core/src/test/resources/remnawave-default-rendered.json').read_text())
    default['outbounds'][0]=json.loads(json.dumps(profiles['vless']))
    configs['remnawave-default']=default
    for config in (auto,split):
        config['inbounds']=[{'tag':'socks-in','protocol':'socks','port':0,'sniffing':{'enabled':True,'destOverride':['http','tls'],'routeOnly':True}}]
    (LAB/'server.json').write_text(json.dumps(server));(LAB/'profiles.json').write_text(json.dumps({'ca':root,'profiles':configs}))
    return configs

def spawn(binary,config,name):
    path=LAB/(name+'.json');path.write_text(json.dumps(config));cmd([binary,'run','-test','-config',str(path)])
    log=(LAB/(name+'.log')).open('wb');outputs.append(log)
    process=subprocess.Popen([binary,'run','-config',str(path)],stdout=log,stderr=subprocess.STDOUT,**CREATION);owned.append(process);time.sleep(.5)
    if process.poll() is not None:raise RuntimeError('Fixture process failed: '+name)
    return process

def curl(port,url,expect=True,ca=False,timeout=12):
    if ca:
        return https_verified(port,url)
    args=[shutil.which('curl.exe') or shutil.which('curl'),'--silent','--show-error','--fail','--max-time',str(timeout),'--noproxy','','--socks5-hostname','127.0.0.1:'+str(port)]
    if ca:args+=['--cacert',str(LAB/'ca.pem')]
    result=subprocess.run(args+[url],capture_output=True,**CREATION)
    if expect and (result.returncode!=0 or b'signet-local-lab' not in result.stdout):
        (LAB/'curl-last-error.txt').write_bytes(result.stderr);raise RuntimeError('Expected proxied response unavailable')
    if not expect and result.returncode==0:raise RuntimeError('Blocked request unexpectedly succeeded')

def recv_exact(sock,count):
    result=b''
    while len(result)<count:
        chunk=sock.recv(count-len(result))
        if not chunk:raise ConnectionError('Proxy closed connection')
        result+=chunk
    return result

def socks_connection(port,host,target_port):
    sock=socket.create_connection(('127.0.0.1',port),timeout=8)
    try:
        sock.sendall(b'\x05\x01\x00');assert recv_exact(sock,2)==b'\x05\x00'
        try:ip=ipaddress.ip_address(host);address=(b'\x01' if ip.version==4 else b'\x04')+ip.packed
        except ValueError:encoded=host.encode('idna');address=b'\x03'+bytes([len(encoded)])+encoded
        sock.sendall(b'\x05\x01\x00'+address+struct.pack('!H',target_port))
        head=recv_exact(sock,4);assert head[1]==0
        size=4 if head[3]==1 else 16 if head[3]==4 else recv_exact(sock,1)[0]
        recv_exact(sock,size+2);return sock
    except BaseException:sock.close();raise

def https_verified(port,url,server_name=None):
    target=urllib.parse.urlsplit(url)
    context=ssl.create_default_context(cadata=(LAB/'ca.pem').read_text())
    with socks_connection(port,target.hostname,target.port or 443) as plain:
        with context.wrap_socket(plain,server_hostname=server_name or target.hostname) as secure:
            secure.sendall(('GET / HTTP/1.1\r\nHost: '+target.netloc+'\r\nConnection: close\r\n\r\n').encode())
            body=b''
            while len(body)<65536:
                chunk=secure.recv(8192)
                if not chunk:break
                body+=chunk
            if b'signet-local-lab' not in body:raise RuntimeError('Verified HTTPS response unavailable')

def udp_dns(port):
    with socket.create_connection(('127.0.0.1',port),timeout=5) as tcp:
        tcp.sendall(b'\x05\x01\x00');assert tcp.recv(2)==b'\x05\x00'
        tcp.sendall(b'\x05\x03\x00\x01\x00\x00\x00\x00\x00\x00');response=tcp.recv(32);assert response[1]==0 and response[3]==1
        address=socket.inet_ntoa(response[4:8]);remote=struct.unpack('!H',response[8:10])[0]
        query=b'\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x04echo\x06signet\x04test\x00\x00\x01\x00\x01'
        packet=b'\x00\x00\x00\x01'+socket.inet_aton('198.18.0.3')+struct.pack('!H',53)+query
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
            udp.settimeout(8);udp.sendto(packet,(address if address!='0.0.0.0' else '127.0.0.1',remote));raw,_=udp.recvfrom(4096)
            assert b'\x12\x34\x81\x80' in raw and raw.endswith(socket.inet_aton('198.18.0.1'))

def host_tests(binary,configs):
    results=[]
    for index,(name,config) in enumerate(configs.items()):
        process=None;row={'case':name}
        try:
            port=10980+index;client=json.loads(json.dumps(config));client['log']={'loglevel':'warning'};client['inbounds']=[{'tag':'socks-in','listen':'127.0.0.1','port':port,'protocol':'socks','settings':{'udp':True}}]
            process=spawn(binary,client,'client-'+name)
            curl(port,'http://198.18.0.1:18480/');row['tcp']='PASS'
            curl(port,'https://198.18.0.2:18443/',ca=True);row['httpsVerified']='PASS'
            udp_dns(port);row['udpDns']='PASS'
            if name in ('remnawave-auto','remnawave-split'):
                curl(port,'http://allowed.signet.test:18480/');row['domainDns']='PASS'
                curl(port,'http://blocked.signet.test:18480/',expect=False,timeout=4);row['routingBlock']='PASS'
            if name=='remnawave-split':
                curl(port,'http://direct.signet.test:18480/');row['geoipDirect']='PASS'
                curl(port,'http://google.com:18480/',expect=False,timeout=4);row['geositeBlock']='PASS'
            try:https_verified(port,'https://198.18.0.2:18443/',server_name='wrong.signet.test')
            except ssl.SSLCertVerificationError:row['tlsNameReject']='PASS'
            else:raise RuntimeError('TLS hostname validation did not reject wrong name')
            row['result']='PASS'
        except Exception as error:row['result']='FAIL';row['reason']=type(error).__name__
        finally:
            if process:process.terminate();process.wait(timeout=10)
        results.append(row);print(json.dumps(row),flush=True)
    out=ROOT/'tests/evidence/0.2.0';out.mkdir(parents=True,exist_ok=True)
    (out/'transport-host.json').write_text(json.dumps({'core':'26.11.9','scope':'Host loopback core, not Android TUN/physical device','cases':results},indent=2)+'\n')
    return all(r['result']=='PASS' for r in results)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serve',action='store_true');parser.add_argument('--case');args=parser.parse_args()
    binary=core();root=certificates();start_services();configs=generate(root,binary)
    spawn(binary,json.loads((LAB/'server.json').read_text()),'server-running')
    print('LOCAL LAB READY',flush=True)
    if args.serve:
        while True:time.sleep(1)
    selected={k:v for k,v in configs.items() if args.case is None or k==args.case}
    if not selected:raise ValueError('Unknown fixture case')
    return 0 if host_tests(binary,selected) else 1

if __name__=='__main__':
    try:raise SystemExit(main())
    finally:
        for process in owned:
            if process.poll() is None:
                process.terminate()
                with contextlib.suppress(subprocess.TimeoutExpired):process.wait(timeout=10)
        for server in servers:server.shutdown();server.server_close()
        for output in outputs:output.close()
