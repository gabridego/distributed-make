import os, subprocess

# get nodes' hostnames
nodes = set()
with open(os.environ['OAR_NODE_FILE'], "r") as f:
    for line in f:
        #nodes.add('"akka://ClusterSystem@' + line.strip() + '"')
        nodes.add(line.strip())


# get nodes's ips and save them in a file to access from nodes (nfs)
node_ips = []
with open("nodes.txt", "w") as out:
    for node in nodes:
        command = "dig +short " + node
        process = subprocess.Popen(command.split(), stdout=subprocess.PIPE)
        ip = process.communicate()[0]
        node_ips.append('"akka://ClusterSystem@' + ip.strip() + ':25251"')

        out.write(ip)


# convert to string in required format
nodes_list = '[' + ', '.join(node_ips) + ']'
# print(nodes_list)

if not os.path.exists("./src/main/resources"):
    os.makedirs("./src/main/resources")

with open("./src/main/resources/application.conf", "w") as f:
    f.write('akka {\n\tactor {\n\t\tprovider = cluster\n\n\t\tserialization-bindings {\n\t\t\t"fr.ensimag.sysd.CborSerializable" = jackson-cbor\n\t\t}\n\t}\n\tremote {\n\t\tartery {\n\t\t\tcanonical.hostname = "127.0.0.1"\n\t\t\tcanonical.port = 0\n\t\t}\n\t}\n\tcluster {\n\t\tseed-nodes = ' + nodes_list + '\n\t\tdowning-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"\n\t}\n}')

