package com.cg.app.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.eclipse.egit.github.core.Blob;
import org.eclipse.egit.github.core.Commit;
import org.eclipse.egit.github.core.CommitUser;
import org.eclipse.egit.github.core.Reference;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.Tree;
import org.eclipse.egit.github.core.TreeEntry;
import org.eclipse.egit.github.core.TypedResource;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.DataService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cg.app.builder.EmailBuilder;
import com.cg.app.domain.Email;

@Service
public class GitCommitServiceImpl implements GitCommitService {

	@Autowired
	private MailerService mailerService;

	@Autowired
	private EmailBuilder emailBuilder;

	@Override
	// Committing back the updated contents to the same file in the Repository.
	public String doInBackground(GitHubClient client, Repository repository, String branch, String repoName,
			String path, String updatedContent, String commitMessage, String USER) {

		CommitService commitService = null;
		DataService dataService = null;
		TypedResource commitResource = null;

		try {

			RepositoryService repositoryService = new RepositoryService();
			commitService = new CommitService(client);
			dataService = new DataService(client);

			String baseCommitSha = repositoryService.getBranches(repository).get(0).getCommit().getSha();
			RepositoryCommit baseCommit = commitService.getCommit(repository, baseCommitSha);
			String treeSha = baseCommit.getSha();

			Blob blob = new Blob();
			blob.setContent(updatedContent).setEncoding(Blob.ENCODING_UTF8);
			String blob_sha = dataService.createBlob(repository, blob);
			Tree baseTree = dataService.getTree(repository, treeSha);

			// create new tree entry
			TreeEntry treeEntry = new TreeEntry();
			treeEntry.setPath(path);
			treeEntry.setMode(TreeEntry.MODE_BLOB);
			treeEntry.setType(TreeEntry.TYPE_BLOB);
			treeEntry.setSha(blob_sha);
			treeEntry.setSize(blob.getContent().length());

			Collection<TreeEntry> entries = new ArrayList<TreeEntry>();
			entries.add(treeEntry);
			Tree newTree = dataService.createTree(repository, entries, baseTree.getSha());

			Commit commit = new Commit();
			commit.setMessage(commitMessage);
			commit.setTree(newTree);

			UserService userService = new UserService(client);
			CommitUser author = new CommitUser();
			author.setEmail(userService.getEmails().get(0));
			author.setName(USER);
			Calendar now = Calendar.getInstance();
			author.setDate(now.getTime());
			commit.setAuthor(author);
			commit.setCommitter(author);
			List<Commit> listOfCommits = new ArrayList<Commit>();
			listOfCommits.add(new Commit().setSha(baseCommitSha));
			commit.setParents(listOfCommits);

			Commit newCommit = dataService.createCommit(repository, commit);
			// create resource
			commitResource = new TypedResource();
			commitResource.setSha(newCommit.getSha());
			commitResource.setType(TypedResource.TYPE_COMMIT);
			commitResource.setUrl(newCommit.getUrl());
		} catch (Exception e) {
			throw new RuntimeException("Error while updating the data");
		}

		try {
			Reference reference = dataService.getReference(repository, "heads/" + branch);
			reference.setObject(commitResource);
			dataService.editReference(repository, reference, true);

		} catch (IOException e) {

			throw new RuntimeException("Wrong branch data");

		}

		return "Succesfully Updated";
	}

	@Override
	public void sendMail() {
		Email email = emailBuilder.build();
		try {
			mailerService.mailThroughUitility(email);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
