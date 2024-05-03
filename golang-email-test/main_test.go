package main

import (
	"context"
	"fmt"
	"github.com/antihax/optional"
	mailslurp "github.com/mailslurp/mailslurp-client-go"
	"github.com/stretchr/testify/assert"
	"os"
	"regexp"
	"testing"
)

var apiKey = os.Getenv("API_KEY")

func getMailSlurpClient(t *testing.T) (*mailslurp.APIClient, context.Context) {
	assert.NotNil(t, apiKey)

	//<gen>golang_init_client
	// go get github.com/mailslurp/mailslurp-client-go
	// create a context with your api key
	ctx := context.WithValue(context.Background(), mailslurp.ContextAPIKey, mailslurp.APIKey{Key: apiKey})

	// create mailslurp client
	config := mailslurp.NewConfiguration()
	client := mailslurp.NewAPIClient(config)
	//</gen>

	return client, ctx
}

func Test_CanCreateInbox(t *testing.T) {
	// create a context with your api key
	client, ctx := getMailSlurpClient(t)

	//<gen>golang_create_inbox
	// create an inbox using the inbox controller
	opts := &mailslurp.CreateInboxOpts{}
	inbox, response, err := client.InboxControllerApi.CreateInbox(ctx, opts)

	assert.NoError(t, err)
	assert.Equal(t, response.StatusCode, 201)
	assert.Contains(t, inbox.EmailAddress, "@mailslurp.net")
	//</gen>
}

func Test_CanSendEmail(t *testing.T) {
	// create a context with your api key
	client, ctx := getMailSlurpClient(t)

	// create an inbox we can send email from
	inbox, _, _ := client.InboxControllerApi.CreateInbox(ctx, nil)

	//<gen>golang_send_email
	// send email from inbox
	sendEmailOptions := mailslurp.SendEmailOptions{
		To:      []string{inbox.EmailAddress},
		Subject: "Test email",
		Body:    "<h1>MailSlurp supports HTML</h1>",
		IsHTML:  true,
	}
	opts := &mailslurp.SendEmailOpts{
		SendEmailOptions: optional.NewInterface(sendEmailOptions),
	}
	res, err := client.InboxControllerApi.SendEmail(ctx, inbox.Id, opts)

	assert.NoError(t, err)
	assert.Equal(t, res.StatusCode, 201)
	//</gen>
}

func Test_CanReceiveEmail(t *testing.T) {
	// create a context with your api key
	client, ctx := getMailSlurpClient(t)

	// create two inboxes for testing
	inbox1, _, _ := client.InboxControllerApi.CreateInbox(ctx, nil)
	inbox2, _, _ := client.InboxControllerApi.CreateInbox(ctx, nil)

	// send email from inbox1 to inbox2
	sendEmailOptions := mailslurp.SendEmailOptions{
		To:      []string{inbox2.EmailAddress},
		Subject: "Hello inbox2",
		Body:    "Your code is: 123",
	}
	sendOpts := &mailslurp.SendEmailOpts{
		SendEmailOptions: optional.NewInterface(sendEmailOptions),
	}
	res, err := client.InboxControllerApi.SendEmail(ctx, inbox1.Id, sendOpts)

	assert.NoError(t, err)
	assert.Equal(t, res.StatusCode, 201)

	// fetch the email for inbox2
	//<gen>golang_wait_for_email
	waitOpts := &mailslurp.WaitForLatestEmailOpts{
		InboxId:    optional.NewInterface(inbox2.Id),
		Timeout:    optional.NewInt64(30000),
		UnreadOnly: optional.NewBool(true),
	}
	email, _, err := client.WaitForControllerApi.WaitForLatestEmail(ctx, waitOpts)
	assert.NoError(t, err)
	assert.Contains(t, email.Subject, "Hello inbox2")
	assert.Contains(t, email.Body, "Your code is")
	// can extract the contents
	r := regexp.MustCompile(`Your code is: ([0-9]{3})`)
	code := r.FindStringSubmatch(email.Body)[1]
	assert.Equal(t, code, "123")
	//</gen>

	//<gen>golang_list_and_delete
	// can list emails
	listOpts := &mailslurp.GetInboxEmailsPaginatedOpts{
		Page: optional.NewInt32(0),
		Size: optional.NewInt32(10),
		Sort: optional.NewString("DESC"),
	}
	emails, _, err := client.InboxControllerApi.GetInboxEmailsPaginated(ctx, inbox2.Id, listOpts)
	assert.NoError(t, err)
	fmt.Println(emails.Content[0].Subject)
	//</gen>
}
