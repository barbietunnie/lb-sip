/*
    Simple node client
    Igor Delac (igor.delac@gmail.com)

*/
#include<stdio.h> //printf
#include<string.h> //memset
#include<stdlib.h> //exit(0);
#include<time.h> //sleep
#include<arpa/inet.h>
#include<sys/socket.h>

#define SERVER "127.0.0.1"
#define BUFLEN 512  //Max length of buffer
#define PORT 5556   //The port on which to send data
#define COUNT 10   //How many packets to send

void die(char *s)
{
    perror(s);
    exit(1);
}

int main(int argc, char* argv[])
{
    struct sockaddr_in si_other;
    int s, i, slen=sizeof(si_other);
    char buf[BUFLEN];
    char message[BUFLEN] = "test123\n";

    char* server = SERVER;
    int port = PORT;
    int count = COUNT;

    if ( (s=socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
    {
        die("socket");
    }

    if ( (argc) == 1)
    {
	    die("Usage:\n udp_client -s {ip_address} -p {udp_port} [-c {packets}]\n");
    }
    else
    {
        for (i = 1; i < argc; i++)
        {
            if (strcmp(argv[i], "-s") == 0)
            {
		server = argv[++i];
            }
	    else if (strcmp(argv[i], "-p") == 0)
	    {
		port = atol(argv[++i]);
	    }
	    else if (strcmp(argv[i], "-c") == 0)
	    {
		count = atol(argv[++i]);
	    }
	    else
	    {
		printf("Invalid: %s\n", argv[i]);
		return 1;
	    }
	}

	printf("Server: %s, port: %i, packets: %i\n", server, port, count);
    }


    memset((char *) &si_other, 0, sizeof(si_other));
    si_other.sin_family = AF_INET;
    si_other.sin_port = htons(port);

    if (inet_aton(server , &si_other.sin_addr) == 0)
    {
        fprintf(stderr, "inet_aton() failed\n");
        exit(1);
    }

    int counter;
    for (counter = 0; counter < count; counter++) {
        //send the message
        if (sendto(s, message, strlen(message) , 0 , (struct sockaddr *) &si_other, slen)==-1)
        {
            die("sendto()");
        }

        memset(buf,'\0', BUFLEN);
	
	float percent = (counter * 100) / count;
	printf("%.2f \%\n", percent);
	fflush(stdout);
	
        // sleep 1 sec.
        sleep(1);
    }

    printf("100.00 \%\n");
    close(s);
    return 0;
}
